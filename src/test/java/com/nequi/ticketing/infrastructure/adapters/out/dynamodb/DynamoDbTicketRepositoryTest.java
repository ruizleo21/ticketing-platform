package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nequi.ticketing.domain.exception.InsufficientInventoryException;
import com.nequi.ticketing.domain.exception.TicketNotAvailableException;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Ticket;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.domain.model.TicketStatus;

import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@ExtendWith(MockitoExtension.class)
class DynamoDbTicketRepositoryTest {

  private static final EventId EVENT_ID = new EventId("event-1");
  private static final TicketId TICKET_1 = new TicketId("A-001");
  private static final TicketId TICKET_2 = new TicketId("A-002");
  private static final String ORDER_ID = "order-1";
  private static final Instant NOW = Instant.parse("2030-06-15T10:00:00Z");
  private static final Instant RESERVED_UNTIL = NOW.plusSeconds(600);

  @Mock
  private DynamoDbAsyncClient client;

  private DynamoDbTicketRepository repository;

  @BeforeEach
  void setUp() {
    repository = new DynamoDbTicketRepository(client);
  }

  @Test
  void shouldCreateTicketsInTransactionsOfAtMostOneHundred() {
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

    StepVerifier.create(repository.createForEvent(EVENT_ID, 101)).verifyComplete();

    ArgumentCaptor<TransactWriteItemsRequest> captor =
        ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
    verify(client, org.mockito.Mockito.times(2)).transactWriteItems(captor.capture());
    List<TransactWriteItemsRequest> requests = captor.getAllValues();
    assertEquals(100, requests.get(0).transactItems().size());
    assertEquals(1, requests.get(1).transactItems().size());
    assertEquals(DynamoDbTables.TICKETS, requests.get(0).transactItems().get(0).put().tableName());
    assertEquals("event-1", requests.get(0).transactItems().get(0).put().item().get("eventId").s());
    assertEquals("A-001", requests.get(0).transactItems().get(0).put().item().get("ticketId").s());
    assertEquals("AVAILABLE", requests.get(0).transactItems().get(0).put().item().get("status").s());
    assertEquals("A-101", requests.get(1).transactItems().get(0).put().item().get("ticketId").s());
  }

  @Test
  void shouldCompleteCreatingZeroTicketsWithoutCallingDynamoDb() {
    StepVerifier.create(repository.createForEvent(EVENT_ID, 0)).verifyComplete();
    verify(client, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
  }

  @Test
  void shouldFindTicketsByIdsInRequestedOrder() {
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      String id = request.key().get("ticketId").s();
      return completedItem(ticketItem(id, TicketStatus.AVAILABLE, null, null));
    });

    StepVerifier.create(repository.findByIds(EVENT_ID, List.of(TICKET_1, TICKET_2)))
        .expectNext(List.of(
            ticket(TICKET_1, TicketStatus.AVAILABLE, null, null),
            ticket(TICKET_2, TicketStatus.AVAILABLE, null, null)))
        .verifyComplete();

    ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
    verify(client, org.mockito.Mockito.times(2)).getItem(captor.capture());
    assertEquals("A-001", captor.getAllValues().get(0).key().get("ticketId").s());
    assertEquals("A-002", captor.getAllValues().get(1).key().get("ticketId").s());
    assertEquals(Boolean.TRUE, captor.getAllValues().get(0).consistentRead());
  }

  @Test
  void shouldRejectNullOrEmptyTicketListWhenReserving() {
    StepVerifier.create(repository.reserve(EVENT_ID, null, ORDER_ID, RESERVED_UNTIL))
        .expectErrorMatches(error -> error instanceof IllegalArgumentException
            && error.getMessage().equals("At least one ticket is required"))
        .verify();
    StepVerifier.create(repository.reserve(EVENT_ID, List.of(), ORDER_ID, RESERVED_UNTIL))
        .expectErrorMatches(error -> error instanceof IllegalArgumentException
            && error.getMessage().equals("At least one ticket is required"))
        .verify();
    verify(client, never()).getItem(any(GetItemRequest.class));
  }

  @Test
  void shouldRejectDuplicateTicketIdsWhenReserving() {
    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1, TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectErrorMatches(error -> error instanceof IllegalArgumentException
            && error.getMessage().equals("Duplicated ticket ids are not allowed"))
        .verify();
    verify(client, never()).getItem(any(GetItemRequest.class));
  }

  @Test
  void shouldRejectReservationWhenTicketDoesNotExist() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));

    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectError(TicketNotAvailableException.class)
        .verify();

    verify(client, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
  }

  @Test
  void shouldRejectReservationWhenTicketIsNotAvailable() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(completedItem(ticketItem("A-001", TicketStatus.RESERVED, ORDER_ID, RESERVED_UNTIL)));

    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectErrorMatches(error -> error instanceof TicketNotAvailableException
            && error.getMessage().contains("Current status: RESERVED"))
        .verify();

    verify(client, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
  }

  @Test
  void shouldRejectReservationWhenInventoryDoesNotExistOrIsInsufficient() {
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      if (request.key().containsKey("ticketId")) {
        return completedItem(ticketItem(request.key().get("ticketId").s(),
            TicketStatus.AVAILABLE, null, null));
      }
      return CompletableFuture.completedFuture(GetItemResponse.builder().build());
    });
    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectError(InsufficientInventoryException.class)
        .verify();
    verify(client, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
  }

  @Test
  void shouldFailReservationWhenInventoryAvailableTicketsAttributeIsMissing() {
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      if (request.key().containsKey("ticketId")) {
        return completedItem(ticketItem("A-001", TicketStatus.AVAILABLE, null, null));
      }
      return completedItem(Map.of("eventId", AttributeValue.fromS(EVENT_ID.value())));
    });

    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectErrorMatches(error -> error instanceof IllegalStateException
            && error.getMessage().contains("availableTickets"))
        .verify();
    verify(client, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
  }

  @Test
  void shouldReserveTicketsAndInventoryAtomicallyThenReturnReservedTickets() {
    AtomicInteger ticketReadCount = new AtomicInteger();
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      if (request.key().containsKey("ticketId")) {
        TicketStatus status = ticketReadCount.getAndIncrement() == 0
            ? TicketStatus.AVAILABLE : TicketStatus.RESERVED;
        return completedItem(ticketItem("A-001", status, status == TicketStatus.RESERVED ? ORDER_ID : null,
            status == TicketStatus.RESERVED ? RESERVED_UNTIL : null));
      }
      return completedItem(Map.of("eventId", AttributeValue.fromS(EVENT_ID.value()),
          "availableTickets", AttributeValue.fromN("10")));
    });
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectNext(List.of(ticket(TICKET_1, TicketStatus.RESERVED, ORDER_ID, RESERVED_UNTIL)))
        .verifyComplete();
    ArgumentCaptor<TransactWriteItemsRequest> captor =
        ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
    verify(client).transactWriteItems(captor.capture());
    List<TransactWriteItem> actions = captor.getValue().transactItems();
    assertEquals(2, actions.size());
    assertEquals(DynamoDbTables.TICKETS, actions.get(0).update().tableName());
    assertEquals(DynamoDbTables.INVENTORY, actions.get(1).update().tableName());
    assertEquals("attribute_exists(eventId) AND availableTickets >= :quantity",
        actions.get(1).update().conditionExpression());
  }

  @Test
  void shouldMapReservationTransactionCancellationToTicketNotAvailable() {
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      if (request.key().containsKey("ticketId")) {
        return completedItem(ticketItem("A-001", TicketStatus.AVAILABLE, null, null));
      }
      return completedItem(Map.of("eventId", AttributeValue.fromS(EVENT_ID.value()),
          "availableTickets", AttributeValue.fromN("10")));
    });
    TransactionCanceledException cancelled = TransactionCanceledException.builder()
        .message("transaction cancelled").build();
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(cancelled));

    StepVerifier.create(repository.reserve(EVENT_ID, List.of(TICKET_1), ORDER_ID, RESERVED_UNTIL))
        .expectError(TicketNotAvailableException.class)
        .verify();
  }

  @Test
  void shouldMoveTicketsToPendingConfirmationAndReadUpdatedTickets() {
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      return completedItem(ticketItem(request.key().get("ticketId").s(),
          TicketStatus.PENDING_CONFIRMATION, ORDER_ID, null));
    });

    StepVerifier.create(repository.moveToPendingConfirmation(EVENT_ID, List.of(TICKET_1), ORDER_ID))
        .expectNext(List.of(ticket(TICKET_1, TicketStatus.PENDING_CONFIRMATION, ORDER_ID, null)))
        .verifyComplete();

    ArgumentCaptor<TransactWriteItemsRequest> captor =
        ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
    verify(client).transactWriteItems(captor.capture());
    TransactWriteItem action = captor.getValue().transactItems().getFirst();
    assertEquals("#status = :expected", action.update().conditionExpression());
    assertEquals("PENDING_CONFIRMATION", action.update().expressionAttributeValues().get(":target").s());
  }

  @Test
  void shouldConfirmSaleAndUpdateInventoryInSameTransaction() {
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      return completedItem(ticketItem(request.key().get("ticketId").s(), TicketStatus.SOLD, ORDER_ID, null));
    });

    StepVerifier.create(repository.confirmSale(EVENT_ID, List.of(TICKET_1), ORDER_ID))
        .expectNext(List.of(ticket(TICKET_1, TicketStatus.SOLD, ORDER_ID, null)))
        .verifyComplete();

    ArgumentCaptor<TransactWriteItemsRequest> captor =
        ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
    verify(client).transactWriteItems(captor.capture());
    assertEquals(2, captor.getValue().transactItems().size());
    assertEquals(DynamoDbTables.INVENTORY,
        captor.getValue().transactItems().get(1).update().tableName());
  }

  @Test
  void shouldMapConfirmSaleTransactionCancellation() {
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(
            TransactionCanceledException.builder().message("cancelled").build()));

    StepVerifier.create(repository.confirmSale(EVENT_ID, List.of(TICKET_1), ORDER_ID))
        .expectErrorMatches(error -> error instanceof IllegalStateException
            && error.getMessage().equals("Tickets could not be confirmed"))
        .verify();
  }

  @Test
  void shouldReleaseTicketsAndInventoryInSameTransaction() {
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    when(client.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
      GetItemRequest request = invocation.getArgument(0);
      return completedItem(ticketItem(request.key().get("ticketId").s(), TicketStatus.AVAILABLE, null, null));
    });

    StepVerifier.create(repository.release(EVENT_ID, List.of(TICKET_1), ORDER_ID))
        .expectNext(List.of(ticket(TICKET_1, TicketStatus.AVAILABLE, null, null)))
        .verifyComplete();

    ArgumentCaptor<TransactWriteItemsRequest> captor =
        ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
    verify(client).transactWriteItems(captor.capture());
    List<TransactWriteItem> actions = captor.getValue().transactItems();
    assertEquals(2, actions.size());
    assertEquals("attribute_exists(eventId) AND (#status = :reserved OR #status = :pending) AND orderId = :orderId",
        actions.get(0).update().conditionExpression());
    assertEquals(DynamoDbTables.INVENTORY, actions.get(1).update().tableName());
  }

  @Test
  void shouldFindTicketsForEventAndMapOptionalTicketAttributes() {
    Map<String, AttributeValue> availableItem = ticketItem("A-001", TicketStatus.AVAILABLE, null, null);
    Map<String, AttributeValue> reservedItem = ticketItem("A-002", TicketStatus.RESERVED,
        ORDER_ID, RESERVED_UNTIL);
    when(client.query(any(QueryRequest.class))).thenReturn(CompletableFuture.completedFuture(
        QueryResponse.builder().items(List.of(availableItem, reservedItem)).build()));

    StepVerifier.create(repository.findByEventId(EVENT_ID))
        .expectNext(ticket(TICKET_1, TicketStatus.AVAILABLE, null, null))
        .expectNext(ticket(TICKET_2, TicketStatus.RESERVED, ORDER_ID, RESERVED_UNTIL))
        .verifyComplete();

    ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
    verify(client).query(captor.capture());
    assertEquals(DynamoDbTables.TICKETS, captor.getValue().tableName());
    assertEquals("eventId = :eventId", captor.getValue().keyConditionExpression());
    assertEquals(EVENT_ID.value(), captor.getValue().expressionAttributeValues().get(":eventId").s());
  }

  @Test
  void shouldFindExpiredReservedTicketsAndBuildScanFilter() {
    when(client.scan(any(ScanRequest.class))).thenReturn(CompletableFuture.completedFuture(
        ScanResponse.builder().items(List.of(
            ticketItem("A-001", TicketStatus.RESERVED, ORDER_ID, RESERVED_UNTIL))).build()));

    StepVerifier.create(repository.findExpired(NOW))
        .expectNext(ticket(TICKET_1, TicketStatus.RESERVED, ORDER_ID, RESERVED_UNTIL))
        .verifyComplete();

    ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
    verify(client).scan(captor.capture());
    assertEquals(DynamoDbTables.TICKETS, captor.getValue().tableName());
    assertEquals("#status = :reserved AND reservedUntil <= :now", captor.getValue().filterExpression());
    assertEquals("RESERVED", captor.getValue().expressionAttributeValues().get(":reserved").s());
    assertEquals(NOW.toString(), captor.getValue().expressionAttributeValues().get(":now").s());
  }

  @Test
  void shouldPropagateTransactionFailureForTicketCreation() {
    RuntimeException failure = new RuntimeException("DynamoDB unavailable");
    when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(failure));

    StepVerifier.create(repository.createForEvent(EVENT_ID, 1))
        .expectErrorSatisfies(actual -> assertSame(failure, actual))
        .verify();
  }

  private static CompletableFuture<GetItemResponse> completedItem(Map<String, AttributeValue> item) {
    return CompletableFuture.completedFuture(GetItemResponse.builder().item(item).build());
  }

  private static Map<String, AttributeValue> ticketItem(
      String id, TicketStatus status, String orderId, Instant reservedUntil) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("eventId", AttributeValue.fromS(EVENT_ID.value()));
    item.put("ticketId", AttributeValue.fromS(id));
    item.put("status", AttributeValue.fromS(status.name()));
    if (orderId != null) {
      item.put("orderId", AttributeValue.fromS(orderId));
    }
    if (reservedUntil != null) {
      item.put("reservedUntil", AttributeValue.fromS(reservedUntil.toString()));
    }
    return item;
  }

  private static Ticket ticket(TicketId id, TicketStatus status, String orderId, Instant reservedUntil) {
    return new Ticket(id, EVENT_ID, status, orderId, reservedUntil);
  }
}
