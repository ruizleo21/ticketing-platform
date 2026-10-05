package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;

import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

@ExtendWith(MockitoExtension.class)
class DynamoDbOrderRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2030-06-15T09:50:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2030-06-15T10:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2030-06-15T10:10:00Z");
  private static final Instant NOW = Instant.parse("2030-06-15T10:05:00Z");
  private static final OrderId ORDER_ID = new OrderId("order-1");
  private static final EventId EVENT_ID = new EventId("event-1");
  private static final List<TicketId> TICKET_IDS = List.of(
      new TicketId("ticket-1"), new TicketId("ticket-2"));
  private static final String IDEMPOTENCY_KEY = "idem-1";

  @Mock
  private DynamoDbAsyncClient client;

  private DynamoDbOrderRepository repository;

  @BeforeEach
  void setUp() {
    repository = new DynamoDbOrderRepository(client);
  }

  @Test
  void shouldSaveOrderWithReservationExpirationAndMapAllAttributes() {
    Order order = order(OrderStatus.RESERVED, EXPIRES_AT);
    when(client.putItem(any(PutItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

    StepVerifier.create(repository.save(order))
        .expectNext(order)
        .verifyComplete();

    ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(client).putItem(captor.capture());
    PutItemRequest request = captor.getValue();
    Map<String, AttributeValue> item = request.item();

    assertEquals(DynamoDbTables.ORDERS, request.tableName());
    assertEquals("order-1", item.get("orderId").s());
    assertEquals("event-1", item.get("eventId").s());
    assertEquals("user-1", item.get("userId").s());
    assertEquals(List.of("ticket-1", "ticket-2"),
        item.get("ticketIds").l().stream().map(AttributeValue::s).toList());
    assertEquals("2", item.get("quantity").n());
    assertEquals("RESERVED", item.get("status").s());
    assertEquals(CREATED_AT.toString(), item.get("createdAt").s());
    assertEquals(UPDATED_AT.toString(), item.get("updatedAt").s());
    assertEquals(EXPIRES_AT.toString(), item.get("reservationExpiresAt").s());
    assertEquals(IDEMPOTENCY_KEY, item.get("idempotencyKey").s());
  }

  @Test
  void shouldOmitReservationExpirationWhenSavingSoldOrder() {
    Order order = order(OrderStatus.SOLD, null);
    when(client.putItem(any(PutItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

    StepVerifier.create(repository.save(order))
        .expectNext(order)
        .verifyComplete();

    ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(client).putItem(captor.capture());
    assertFalse(captor.getValue().item().containsKey("reservationExpiresAt"));
  }

  @Test
  void shouldPropagatePutItemFailure() {
    RuntimeException failure = new RuntimeException("DynamoDB unavailable");
    when(client.putItem(any(PutItemRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(failure));

    StepVerifier.create(repository.save(order(OrderStatus.RESERVED, EXPIRES_AT)))
        .expectErrorSatisfies(actual -> assertSame(failure, actual))
        .verify();
  }

  @Test
  void shouldFindOrderByIdAndMapItemWithReservationExpiration() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            GetItemResponse.builder().item(item(OrderStatus.RESERVED, EXPIRES_AT)).build()));

    StepVerifier.create(repository.findById(ORDER_ID))
        .expectNext(order(OrderStatus.RESERVED, EXPIRES_AT))
        .verifyComplete();

    ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
    verify(client).getItem(captor.capture());
    assertEquals(DynamoDbTables.ORDERS, captor.getValue().tableName());
    assertEquals(ORDER_ID.value(), captor.getValue().key().get("orderId").s());
  }

  @Test
  void shouldReturnEmptyWhenOrderIdDoesNotExist() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));

    StepVerifier.create(repository.findById(ORDER_ID)).verifyComplete();

    verify(client).getItem(any(GetItemRequest.class));
  }

  @Test
  void shouldFindOrderByIdempotencyKey() {
    when(client.query(any(QueryRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            QueryResponse.builder().items(List.of(item(OrderStatus.RESERVED, EXPIRES_AT))).build()));

    StepVerifier.create(repository.findByIdempotencyKey(IDEMPOTENCY_KEY))
        .expectNext(order(OrderStatus.RESERVED, EXPIRES_AT))
        .verifyComplete();

    ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
    verify(client).query(captor.capture());
    QueryRequest request = captor.getValue();
    assertEquals(DynamoDbTables.ORDERS, request.tableName());
    assertEquals("idempotency-index", request.indexName());
    assertEquals("idempotencyKey = :key", request.keyConditionExpression());
    assertEquals(IDEMPOTENCY_KEY, request.expressionAttributeValues().get(":key").s());
    assertEquals(1, request.limit());
  }

  @Test
  void shouldReturnEmptyWhenIdempotencyKeyIsNotFound() {
    when(client.query(any(QueryRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(QueryResponse.builder().items(List.of()).build()));

    StepVerifier.create(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).verifyComplete();
  }

  @Test
  void shouldFindExpiredReservationsAndBuildScanFilter() {
    Order reserved = order(OrderStatus.RESERVED, EXPIRES_AT);
    Order pending = orderWithId("order-2", OrderStatus.PENDING_CONFIRMATION, EXPIRES_AT);
    when(client.scan(any(ScanRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ScanResponse.builder().items(List.of(
                item(reserved), item(pending))).build()));

    StepVerifier.create(repository.findExpiredReservations(NOW))
        .expectNext(reserved)
        .expectNext(pending)
        .verifyComplete();

    ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
    verify(client).scan(captor.capture());
    ScanRequest request = captor.getValue();
    assertEquals(DynamoDbTables.ORDERS, request.tableName());
    assertEquals("#status IN (:reserved, :pending) AND reservationExpiresAt <= :now",
        request.filterExpression());
    assertEquals("status", request.expressionAttributeNames().get("#status"));
    assertEquals("RESERVED", request.expressionAttributeValues().get(":reserved").s());
    assertEquals("PENDING_CONFIRMATION", request.expressionAttributeValues().get(":pending").s());
    assertEquals(NOW.toString(), request.expressionAttributeValues().get(":now").s());
  }

  @Test
  void shouldReturnEmptyWhenNoExpiredReservationsExist() {
    when(client.scan(any(ScanRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(ScanResponse.builder().items(List.of()).build()));

    StepVerifier.create(repository.findExpiredReservations(NOW)).verifyComplete();
  }

  @Test
  void shouldMapOrderWithoutReservationExpiration() {
    Order sold = order(OrderStatus.SOLD, null);
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            GetItemResponse.builder().item(item(sold)).build()));

    StepVerifier.create(repository.findById(ORDER_ID))
        .assertNext(actual -> {
          assertEquals(sold, actual);
          assertNull(actual.reservationExpiresAt());
        })
        .verifyComplete();
  }

  private static Order order(OrderStatus status, Instant expiresAt) {
    return orderWithId(ORDER_ID.value(), status, expiresAt);
  }

  private static Order orderWithId(String id, OrderStatus status, Instant expiresAt) {
    return new Order(new OrderId(id), EVENT_ID, "user-1", TICKET_IDS, status,
        CREATED_AT, UPDATED_AT, expiresAt, IDEMPOTENCY_KEY);
  }

  private static Map<String, AttributeValue> item(OrderStatus status, Instant expiresAt) {
    return item(order(status, expiresAt));
  }

  private static Map<String, AttributeValue> item(Order order) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("orderId", AttributeValue.fromS(order.id().value()));
    item.put("eventId", AttributeValue.fromS(order.eventId().value()));
    item.put("userId", AttributeValue.fromS(order.userId()));
    item.put("ticketIds", AttributeValue.fromL(order.ticketIds().stream()
        .map(ticketId -> AttributeValue.fromS(ticketId.value())).toList()));
    item.put("quantity", AttributeValue.fromN(String.valueOf(order.quantity())));
    item.put("status", AttributeValue.fromS(order.status().name()));
    item.put("createdAt", AttributeValue.fromS(order.createdAt().toString()));
    item.put("updatedAt", AttributeValue.fromS(order.updatedAt().toString()));
    item.put("idempotencyKey", AttributeValue.fromS(order.idempotencyKey()));
    if (order.reservationExpiresAt() != null) {
      item.put("reservationExpiresAt", AttributeValue.fromS(order.reservationExpiresAt().toString()));
    }
    return item;
  }
}
