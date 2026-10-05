package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.nequi.ticketing.application.port.out.OrderRepository;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

@Repository
public class DynamoDbOrderRepository implements OrderRepository {

  private final DynamoDbAsyncClient client;

  public DynamoDbOrderRepository(DynamoDbAsyncClient client) {
    this.client = client;
  }

  @Override
  public Mono<Order> save(Order order) {
    Map<String, AttributeValue> item = new HashMap<>();

    item.put("orderId", AttributeValue.fromS(order.id().value()));
    item.put("eventId", AttributeValue.fromS(order.eventId().value()));
    item.put("userId", AttributeValue.fromS(order.userId()));
    item.put("ticketIds", AttributeValue.fromL(
        order.ticketIds().stream()
            .map(ticket -> AttributeValue.fromS(ticket.value()))
            .toList()));
    item.put("quantity", AttributeValue.fromN(String.valueOf(order.quantity())));
    item.put("status", AttributeValue.fromS(order.status().name()));
    item.put("createdAt", AttributeValue.fromS(order.createdAt().toString()));
    item.put("updatedAt", AttributeValue.fromS(order.updatedAt().toString()));

    if (order.reservationExpiresAt() != null) {
      item.put("reservationExpiresAt",
          AttributeValue.fromS(order.reservationExpiresAt().toString()));
    }

    item.put("idempotencyKey", AttributeValue.fromS(order.idempotencyKey()));

    PutItemRequest request = PutItemRequest.builder()
        .tableName(DynamoDbTables.ORDERS)
        .item(item)
        .build();

    return Mono.defer(() -> Mono.fromFuture(client.putItem(request)))
        .thenReturn(order);
  }


  @Override
  public Mono<Order> findById(OrderId orderId) {

    return Mono.fromFuture(client.getItem(GetItemRequest.builder().tableName(DynamoDbTables.ORDERS)
        .key(Map.of("orderId", AttributeValue.fromS(orderId.value()))).build())).flatMap(
        response -> response.hasItem() ? Mono.just(toDomain(response.item())) : Mono.empty());
  }

  @Override
  public Mono<Order> findByIdempotencyKey(String key) {

    return Mono.fromFuture(client.query(
            QueryRequest.builder().tableName(DynamoDbTables.ORDERS).indexName("idempotency-index")
                .keyConditionExpression("idempotencyKey = :key")
                .expressionAttributeValues(Map.of(":key", AttributeValue.fromS(key))).limit(1).build()))
        .flatMap(response -> response.items().isEmpty() ? Mono.empty()
            : Mono.just(toDomain(response.items().getFirst())));
  }

  @Override
  public Flux<Order> findExpiredReservations(Instant now) {

    return Mono.fromFuture(client.scan(ScanRequest.builder().tableName(DynamoDbTables.ORDERS)
            .filterExpression("#status IN (:reserved, :pending) " + "AND reservationExpiresAt <= :now")
            .expressionAttributeNames(Map.of("#status", "status")).expressionAttributeValues(
                Map.of(":reserved", AttributeValue.fromS(OrderStatus.RESERVED.name()), ":pending",
                    AttributeValue.fromS(OrderStatus.PENDING_CONFIRMATION.name()), ":now",
                    AttributeValue.fromS(now.toString()))).build()))
        .flatMapMany(response -> Flux.fromIterable(response.items())).map(this::toDomain);
  }

  private Order toDomain(Map<String, AttributeValue> item) {

    Instant reservationExpiresAt = item.containsKey("reservationExpiresAt") ? Instant.parse(
        item.get("reservationExpiresAt").s()) : null;

    return new Order(new OrderId(item.get("orderId").s()), new EventId(item.get("eventId").s()),
        item.get("userId").s(),
        item.get("ticketIds").l().stream().map(value -> new TicketId(value.s())).toList(),
        OrderStatus.valueOf(item.get("status").s()), Instant.parse(item.get("createdAt").s()),
        Instant.parse(item.get("updatedAt").s()), reservationExpiresAt,
        item.get("idempotencyKey").s());
  }
}