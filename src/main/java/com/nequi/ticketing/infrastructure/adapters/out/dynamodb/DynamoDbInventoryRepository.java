package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import com.nequi.ticketing.application.port.out.InventoryRepository;
import com.nequi.ticketing.domain.exception.InsufficientInventoryException;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

@Repository
public class DynamoDbInventoryRepository implements InventoryRepository {

    private final DynamoDbAsyncClient client;

    public DynamoDbInventoryRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    @Override
    public Mono<Inventory> create(Inventory inventory) {
        var item = Map.of(
                "eventId", AttributeValue.fromS(inventory.eventId().value()),
                "totalTickets", AttributeValue.fromN(String.valueOf(inventory.totalTickets())),
                "availableTickets", AttributeValue.fromN(String.valueOf(inventory.availableTickets())),
                "reservedTickets", AttributeValue.fromN("0"),
                "soldTickets", AttributeValue.fromN("0"),
                "complimentaryTickets", AttributeValue.fromN("0"),
                "version", AttributeValue.fromN("0")
        );
        return Mono.fromFuture(client.putItem(PutItemRequest.builder()
                        .tableName(DynamoDbTables.INVENTORY)
                        .item(item)
                        .conditionExpression("attribute_not_exists(eventId)")
                        .build()))
                .thenReturn(inventory);
    }

    @Override
    public Mono<Inventory> findByEventId(EventId eventId) {
        return Mono.fromFuture(client.getItem(GetItemRequest.builder()
                        .tableName(DynamoDbTables.INVENTORY)
                        .key(Map.of("eventId", AttributeValue.fromS(eventId.value())))
                        .build()))
                .flatMap(response -> response.hasItem()
                        ? Mono.just(toDomain(response.item()))
                        : Mono.empty());
    }

    @Override
    public Mono<Inventory> reserve(EventId eventId, int quantity) {
        return update(eventId, quantity, "reserve");
    }

    @Override
    public Mono<Inventory> releaseReservation(EventId eventId, int quantity) {
        return update(eventId, quantity, "release");
    }

    @Override
    public Mono<Inventory> confirmSale(EventId eventId, int quantity) {
        return update(eventId, quantity, "confirm");
    }

    private Mono<Inventory> update(EventId eventId, int quantity, String operation) {
        String updateExpression;
        String conditionExpression;

        switch (operation) {
            case "reserve" -> {
                updateExpression = "SET availableTickets = availableTickets - :q, " +
                        "reservedTickets = reservedTickets + :q, version = version + :one";
                conditionExpression = "availableTickets >= :q";
            }
            case "release" -> {
                updateExpression = "SET availableTickets = availableTickets + :q, " +
                        "reservedTickets = reservedTickets - :q, version = version + :one";
                conditionExpression = "reservedTickets >= :q";
            }
            case "confirm" -> {
                updateExpression = "SET reservedTickets = reservedTickets - :q, " +
                        "soldTickets = soldTickets + :q, version = version + :one";
                conditionExpression = "reservedTickets >= :q";
            }
            default -> throw new IllegalArgumentException("Unsupported inventory operation");
        }

        var request = UpdateItemRequest.builder()
                .tableName(DynamoDbTables.INVENTORY)
                .key(Map.of("eventId", AttributeValue.fromS(eventId.value())))
                .updateExpression(updateExpression)
                .conditionExpression(conditionExpression)
                .expressionAttributeValues(Map.of(
                        ":q", AttributeValue.fromN(String.valueOf(quantity)),
                        ":one", AttributeValue.fromN("1")
                ))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        return Mono.fromFuture(client.updateItem(request))
                .map(response -> toDomain(response.attributes()))
                .onErrorMap(ConditionalCheckFailedException.class, ignored ->
                        new InsufficientInventoryException());
    }

    private Inventory toDomain(Map<String, AttributeValue> i) {
        return new Inventory(
                new EventId(i.get("eventId").s()),
                Integer.parseInt(i.get("totalTickets").n()),
                Integer.parseInt(i.get("availableTickets").n()),
                Integer.parseInt(i.get("reservedTickets").n()),
                Integer.parseInt(i.get("soldTickets").n()),
                Integer.parseInt(i.getOrDefault("complimentaryTickets", AttributeValue.fromN("0")).n()),
                Long.parseLong(i.get("version").n())
        );
    }
}
