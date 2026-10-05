package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import com.nequi.ticketing.domain.exception.InsufficientInventoryException;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbInventoryRepositoryTest {

  private static final EventId EVENT_ID = new EventId("event-1");

  @Mock
  private DynamoDbAsyncClient client;

  private DynamoDbInventoryRepository repository;

  @BeforeEach
  void setUp() {
    repository = new DynamoDbInventoryRepository(client);
  }

  @Test
  void shouldCreateInventoryAndWriteInitialCountersToDynamoDb() {
    Inventory inventory = new Inventory(EVENT_ID, 100, 100, 0, 0, 0, 0);
    when(client.putItem(any(PutItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

    StepVerifier.create(repository.create(inventory))
        .expectNext(inventory)
        .verifyComplete();

    ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(client).putItem(captor.capture());
    PutItemRequest request = captor.getValue();

    assertEquals(DynamoDbTables.INVENTORY, request.tableName());
    assertEquals("event-1", request.item().get("eventId").s());
    assertEquals("100", request.item().get("totalTickets").n());
    assertEquals("100", request.item().get("availableTickets").n());
    assertEquals("0", request.item().get("reservedTickets").n());
    assertEquals("0", request.item().get("soldTickets").n());
    assertEquals("0", request.item().get("complimentaryTickets").n());
    assertEquals("0", request.item().get("version").n());
    assertEquals("attribute_not_exists(eventId)", request.conditionExpression());
  }

  @Test
  void shouldFindInventoryAndMapAllDynamoDbAttributes() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            GetItemResponse.builder().item(inventoryItem(100, 70, 20, 8, 2, 4, true)).build()));

    StepVerifier.create(repository.findByEventId(EVENT_ID))
        .expectNext(new Inventory(EVENT_ID, 100, 70, 20, 8, 2, 4))
        .verifyComplete();

    ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
    verify(client).getItem(captor.capture());
    assertEquals(DynamoDbTables.INVENTORY, captor.getValue().tableName());
    assertEquals("event-1", captor.getValue().key().get("eventId").s());
  }

  @Test
  void shouldUseZeroComplimentaryTicketsWhenAttributeIsMissing() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            GetItemResponse.builder().item(inventoryItem(100, 80, 10, 10, 0, 3, false)).build()));

    StepVerifier.create(repository.findByEventId(EVENT_ID))
        .expectNext(new Inventory(EVENT_ID, 100, 80, 10, 10, 0, 3))
        .verifyComplete();
  }

  @Test
  void shouldReturnEmptyWhenInventoryDoesNotExist() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));

    StepVerifier.create(repository.findByEventId(EVENT_ID))
        .verifyComplete();

    verify(client).getItem(any(GetItemRequest.class));
  }

  @Test
  void shouldReserveInventoryAndBuildExpectedUpdateRequest() {
    Inventory updated = new Inventory(EVENT_ID, 100, 90, 10, 0, 0, 1);
    when(client.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            UpdateItemResponse.builder().attributes(inventoryItem(100, 90, 10, 0, 0, 1, true)).build()));

    StepVerifier.create(repository.reserve(EVENT_ID, 10))
        .expectNext(updated)
        .verifyComplete();

    assertUpdateRequest(
        "SET availableTickets = availableTickets - :q, reservedTickets = reservedTickets + :q, version = version + :one",
        "availableTickets >= :q", 10);
  }

  @Test
  void shouldReleaseReservationAndBuildExpectedUpdateRequest() {
    Inventory updated = new Inventory(EVENT_ID, 100, 90, 10, 0, 0, 1);
    when(client.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            UpdateItemResponse.builder().attributes(inventoryItem(100, 90, 10, 0, 0, 1, true)).build()));

    StepVerifier.create(repository.releaseReservation(EVENT_ID, 10))
        .expectNext(updated)
        .verifyComplete();

    assertUpdateRequest(
        "SET availableTickets = availableTickets + :q, reservedTickets = reservedTickets - :q, version = version + :one",
        "reservedTickets >= :q", 10);
  }

  @Test
  void shouldConfirmSaleAndBuildExpectedUpdateRequest() {
    Inventory updated = new Inventory(EVENT_ID, 100, 90, 0, 10, 0, 1);
    when(client.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            UpdateItemResponse.builder().attributes(inventoryItem(100, 90, 0, 10, 0, 1, true)).build()));

    StepVerifier.create(repository.confirmSale(EVENT_ID, 10))
        .expectNext(updated)
        .verifyComplete();

    assertUpdateRequest(
        "SET reservedTickets = reservedTickets - :q, soldTickets = soldTickets + :q, version = version + :one",
        "reservedTickets >= :q", 10);
  }

  @Test
  void shouldMapConditionalUpdateFailureToInsufficientInventoryException() {
    ConditionalCheckFailedException awsError = ConditionalCheckFailedException.builder()
        .message("condition failed")
        .build();
    when(client.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(awsError));

    StepVerifier.create(repository.reserve(EVENT_ID, 5))
        .expectError(InsufficientInventoryException.class)
        .verify();
  }

  @Test
  void shouldPropagateNonConditionalUpdateFailureUnchanged() {
    RuntimeException awsError = new RuntimeException("DynamoDB unavailable");
    when(client.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(awsError));

    StepVerifier.create(repository.reserve(EVENT_ID, 5))
        .expectErrorSatisfies(actual -> assertSame(awsError, actual))
        .verify();
  }

  private void assertUpdateRequest(String expectedUpdateExpression,
      String expectedConditionExpression,
      int quantity) {
    ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
    verify(client).updateItem(captor.capture());
    UpdateItemRequest request = captor.getValue();

    assertEquals(DynamoDbTables.INVENTORY, request.tableName());
    assertEquals("event-1", request.key().get("eventId").s());
    assertEquals(expectedUpdateExpression, request.updateExpression());
    assertEquals(expectedConditionExpression, request.conditionExpression());
    assertEquals(String.valueOf(quantity), request.expressionAttributeValues().get(":q").n());
    assertEquals("1", request.expressionAttributeValues().get(":one").n());
    assertEquals(ReturnValue.ALL_NEW, request.returnValues());
  }

  private static Map<String, AttributeValue> inventoryItem(
      int total, int available, int reserved, int sold, int complimentary, long version,
      boolean includeComplimentary) {
    Map<String, AttributeValue> item = new java.util.HashMap<>();
    item.put("eventId", AttributeValue.fromS(EVENT_ID.value()));
    item.put("totalTickets", AttributeValue.fromN(String.valueOf(total)));
    item.put("availableTickets", AttributeValue.fromN(String.valueOf(available)));
    item.put("reservedTickets", AttributeValue.fromN(String.valueOf(reserved)));
    item.put("soldTickets", AttributeValue.fromN(String.valueOf(sold)));
    if (includeComplimentary) {
      item.put("complimentaryTickets", AttributeValue.fromN(String.valueOf(complimentary)));
    }
    item.put("version", AttributeValue.fromN(String.valueOf(version)));
    return item;
  }
}
