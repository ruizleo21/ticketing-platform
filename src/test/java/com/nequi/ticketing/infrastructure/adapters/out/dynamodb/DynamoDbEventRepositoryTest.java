package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nequi.ticketing.domain.model.Event;
import com.nequi.ticketing.domain.model.EventId;

import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

@ExtendWith(MockitoExtension.class)
class DynamoDbEventRepositoryTest {

  private static final Instant NOW = Instant.parse("2030-06-15T10:00:00Z");
  private static final Instant EVENT_DATE = Instant.parse("2030-06-16T18:00:00Z");
  private static final EventId EVENT_ID = new EventId("event-1");

  @Mock
  private DynamoDbAsyncClient client;

  private DynamoDbEventRepository repository;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    repository = new DynamoDbEventRepository(client, clock);
  }

  @Test
  void shouldSaveEventAndMapAllAttributesToDynamoDb() {
    Event event = event(EVENT_ID, EVENT_DATE, 100);
    when(client.putItem(any(PutItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));

    StepVerifier.create(repository.save(event))
        .expectNext(event)
        .verifyComplete();

    ArgumentCaptor<PutItemRequest> requestCaptor =
        ArgumentCaptor.forClass(PutItemRequest.class);
    verify(client).putItem(requestCaptor.capture());

    PutItemRequest request = requestCaptor.getValue();
    assertEquals(DynamoDbTables.EVENTS, request.tableName());
    assertEquals("event-1", request.item().get("eventId").s());
    assertEquals("Concert event-1", request.item().get("name").s());
    assertEquals(EVENT_DATE.toString(), request.item().get("date").s());
    assertEquals("Main Hall", request.item().get("venue").s());
    assertEquals("100", request.item().get("totalCapacity").n());
  }

  @Test
  void shouldFindEventByIdAndMapDynamoDbItem() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            GetItemResponse.builder().item(item("event-1", EVENT_DATE, 100)).build()));

    StepVerifier.create(repository.findById(EVENT_ID))
        .expectNext(event(EVENT_ID, EVENT_DATE, 100))
        .verifyComplete();

    ArgumentCaptor<GetItemRequest> requestCaptor =
        ArgumentCaptor.forClass(GetItemRequest.class);
    verify(client).getItem(requestCaptor.capture());
    assertEquals(DynamoDbTables.EVENTS, requestCaptor.getValue().tableName());
    assertEquals("event-1", requestCaptor.getValue().key().get("eventId").s());
  }

  @Test
  void shouldReturnEmptyWhenEventDoesNotExist() {
    when(client.getItem(any(GetItemRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));

    StepVerifier.create(repository.findById(EVENT_ID))
        .verifyComplete();

    verify(client).getItem(any(GetItemRequest.class));
  }

  @Test
  void shouldFindAllEventsAndMapDynamoDbItems() {
    Instant secondDate = EVENT_DATE.plusSeconds(3600);
    when(client.scan(any(ScanRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ScanResponse.builder()
                .items(List.of(
                    item("event-1", EVENT_DATE, 100),
                    item("event-2", secondDate, 50)))
                .build()));

    StepVerifier.create(repository.findAll())
        .expectNext(event(new EventId("event-1"), EVENT_DATE, 100))
        .expectNext(event(new EventId("event-2"), secondDate, 50))
        .verifyComplete();

    ArgumentCaptor<ScanRequest> requestCaptor = ArgumentCaptor.forClass(ScanRequest.class);
    verify(client).scan(requestCaptor.capture());
    assertEquals(DynamoDbTables.EVENTS, requestCaptor.getValue().tableName());
  }

  @Test
  void shouldReturnOnlyEventsAtOrAfterCurrentInstantAsAvailable() {
    Instant justBeforeNow = NOW.minusNanos(1);
    Instant atNow = NOW;
    Instant afterNow = NOW.plusNanos(1);
    when(client.scan(any(ScanRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ScanResponse.builder()
                .items(List.of(
                    item("past-event", justBeforeNow, 10),
                    item("current-event", atNow, 20),
                    item("future-event", afterNow, 30)))
                .build()));

    StepVerifier.create(repository.findAvailableEvents())
        .assertNext(events -> {
          assertEquals(2, events.size());
          assertEquals(event(new EventId("current-event"), atNow, 20), events.get(0));
          assertEquals(event(new EventId("future-event"), afterNow, 30), events.get(1));
          assertFalse(events.stream().anyMatch(e -> e.id().value().equals("past-event")));
          assertTrue(events.stream().allMatch(e -> !e.date().isBefore(NOW)));
        })
        .verifyComplete();
  }

  @Test
  void shouldReturnEmptyListWhenNoEventsAreAvailableByDate() {
    when(client.scan(any(ScanRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ScanResponse.builder()
                .items(List.of(item("past-event", NOW.minusSeconds(1), 10)))
                .build()));

    StepVerifier.create(repository.findAvailableEvents())
        .expectNext(List.of())
        .verifyComplete();
  }

  private static Event event(EventId id, Instant date, int capacity) {
    return new Event(id, "Concert " + id.value(), date, "Main Hall", capacity);
  }

  private static Map<String, AttributeValue> item(String id, Instant date, int capacity) {
    return Map.of(
        "eventId", AttributeValue.fromS(id),
        "name", AttributeValue.fromS("Concert " + id),
        "date", AttributeValue.fromS(date.toString()),
        "venue", AttributeValue.fromS("Main Hall"),
        "totalCapacity", AttributeValue.fromN(String.valueOf(capacity))
    );
  }
}
