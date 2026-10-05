package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.nequi.ticketing.application.port.out.EventRepository;
import com.nequi.ticketing.domain.model.Event;
import com.nequi.ticketing.domain.model.EventId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

@Repository
public class DynamoDbEventRepository implements EventRepository {

  private final DynamoDbAsyncClient client;
  private final Clock clock;

  public DynamoDbEventRepository(DynamoDbAsyncClient client, Clock clock) {
    this.client = client;
    this.clock = clock;
  }

  @Override
  public Mono<Event> save(Event event) {
    var item = Map.of("eventId", AttributeValue.fromS(event.id().value()), "name",
        AttributeValue.fromS(event.name()), "date", AttributeValue.fromS(event.date().toString()),
        "venue", AttributeValue.fromS(event.venue()), "totalCapacity",
        AttributeValue.fromN(String.valueOf(event.totalCapacity())));

    return Mono.fromFuture(client.putItem(
            PutItemRequest.builder().tableName(DynamoDbTables.EVENTS).item(item).build()))
        .thenReturn(event);
  }

  @Override
  public Mono<Event> findById(EventId eventId) {
    return Mono.fromFuture(client.getItem(GetItemRequest.builder().tableName(DynamoDbTables.EVENTS)
            .key(Map.of("eventId", AttributeValue.fromS(eventId.value()))).build()))
        .flatMap(response -> {
          if (!response.hasItem()) {
            return Mono.empty();
          }
          var i = response.item();
          return Mono.just(new Event(new EventId(i.get("eventId").s()), i.get("name").s(),
              java.time.Instant.parse(i.get("date").s()), i.get("venue").s(),
              Integer.parseInt(i.get("totalCapacity").n())));
        });
  }

  @Override
  public Flux<Event> findAll() {
    return Mono.fromFuture(
            client.scan(ScanRequest.builder().tableName(DynamoDbTables.EVENTS).build()))
        .flatMapMany(response -> Flux.fromIterable(response.items())).map(
            i -> new Event(new EventId(i.get("eventId").s()), i.get("name").s(),
                java.time.Instant.parse(i.get("date").s()), i.get("venue").s(),
                Integer.parseInt(i.get("totalCapacity").n())));
  }

  @Override
  public Mono<List<Event>> findAvailableEvents() {
    return findAll().filter(event -> !event.date().isBefore(Instant.now(clock))).collectList();
  }
}
