package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Repository;

import com.nequi.ticketing.application.port.out.TicketRepository;
import com.nequi.ticketing.domain.exception.InsufficientInventoryException;
import com.nequi.ticketing.domain.exception.TicketNotAvailableException;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Ticket;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.domain.model.TicketStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

@Slf4j
@Repository
public class DynamoDbTicketRepository implements TicketRepository {

  private static final int DYNAMODB_TRANSACTION_LIMIT = 100;

  private final DynamoDbAsyncClient client;

  public DynamoDbTicketRepository(DynamoDbAsyncClient client) {
    this.client = client;
  }

  @Override
  public Mono<Void> createForEvent(EventId eventId, int capacity) {

    List<TransactWriteItem> actions = IntStream.rangeClosed(1, capacity).mapToObj(i -> {

      String ticketId = String.format("A-%03d", i);

      Map<String, AttributeValue> item = Map.of("eventId", AttributeValue.fromS(eventId.value()),
          "ticketId", AttributeValue.fromS(ticketId), "status",
          AttributeValue.fromS(TicketStatus.AVAILABLE.name()));

      return TransactWriteItem.builder().put(
          Put.builder().tableName(DynamoDbTables.TICKETS).item(item)
              .conditionExpression("attribute_not_exists(eventId)").build()).build();
    }).toList();

    return transactInChunks(actions).then();
  }

  @Override
  public Mono<List<Ticket>> findByIds(EventId eventId, List<TicketId> ticketIds) {

    List<Mono<Ticket>> requests = ticketIds.stream().map(ticketId -> findById(eventId, ticketId))
        .toList();

    return Flux.mergeSequential(requests).collectList();
  }

  @Override
  public Mono<List<Ticket>> reserve(EventId eventId, List<TicketId> ticketIds, String orderId,
      Instant reservedUntil) {

    if (ticketIds == null || ticketIds.isEmpty()) {
      return Mono.error(new IllegalArgumentException("At least one ticket is required"));
    }

    /*
     * No permitir el mismo ticket dos veces.
     */
    if (ticketIds.size() != ticketIds.stream().distinct().count()) {
      return Mono.error(new IllegalArgumentException("Duplicated ticket ids are not allowed"));
    }

    /*
     * STEP 1
     *
     * Read the CURRENT state of all requested tickets.
     */
    return findByIds(eventId, ticketIds)

        /*
         * STEP 2
         *
         * Verify that every ticket exists and is AVAILABLE.
         */.flatMap(tickets -> verifyTicketsAreAvailable(ticketIds, tickets))

        /*
         * STEP 3
         *
         * Verify inventory.
         */.then(Mono.defer(() -> verifyInventory(eventId, ticketIds.size())))

        /*
         * STEP 4
         *
         * Only now attempt the atomic reservation.
         */.then(Mono.defer(() -> executeReservationTransaction(eventId, ticketIds, orderId, reservedUntil)));
  }

  @Override
  public Mono<List<Ticket>> moveToPendingConfirmation(EventId eventId, List<TicketId> ticketIds,
      String orderId) {

    List<TransactWriteItem> actions = ticketIds.stream().map(
        ticketId -> transitionAction(eventId, ticketId, orderId, TicketStatus.RESERVED,
            TicketStatus.PENDING_CONFIRMATION, null)).toList();

    return transact(actions).then(findTickets(eventId, ticketIds));
  }

  @Override
  public Mono<List<Ticket>> confirmSale(EventId eventId, List<TicketId> ticketIds, String orderId) {

    List<TransactWriteItem> actions = ticketIds.stream().map(
        ticketId -> transitionAction(eventId, ticketId, orderId, TicketStatus.PENDING_CONFIRMATION,
            TicketStatus.SOLD, null)).collect(Collectors.toCollection(ArrayList::new));

    actions.add(confirmInventoryAction(eventId, ticketIds.size()));

    return transact(actions).then(findTickets(eventId, ticketIds))
        .onErrorMap(TransactionCanceledException.class,
            ignored -> new IllegalStateException("Tickets could not be confirmed"));
  }

  @Override
  public Mono<List<Ticket>> release(EventId eventId, List<TicketId> ticketIds, String orderId) {

    List<TransactWriteItem> actions = ticketIds.stream()
        .map(ticketId -> releaseTicketAction(eventId, ticketId, orderId))
        .collect(Collectors.toCollection(ArrayList::new));

    actions.add(releaseInventoryAction(eventId, ticketIds.size()));

    return transact(actions).then(findTickets(eventId, ticketIds));
  }

  private TransactWriteItem reserveAction(EventId eventId, TicketId ticketId, String orderId,
      Instant reservedUntil) {

    Map<String, AttributeValue> values = new HashMap<>();

    values.put(":available", AttributeValue.fromS(TicketStatus.AVAILABLE.name()));

    values.put(":reserved", AttributeValue.fromS(TicketStatus.RESERVED.name()));

    values.put(":orderId", AttributeValue.fromS(orderId));

    values.put(":reservedUntil", AttributeValue.fromS(reservedUntil.toString()));

    Update update = Update.builder().tableName(DynamoDbTables.TICKETS)
        .key(ticketKey(eventId, ticketId)).updateExpression(
            "SET #status = :reserved, " + "orderId = :orderId, " + "reservedUntil = :reservedUntil")
        .conditionExpression("attribute_exists(eventId) " + "AND #status = :available")
        .expressionAttributeNames(Map.of("#status", "status")).expressionAttributeValues(values)
        .build();

    return TransactWriteItem.builder().update(update).build();
  }

  private TransactWriteItem transitionAction(EventId eventId, TicketId ticketId, String orderId,
      TicketStatus expected, TicketStatus target, Instant reservedUntil) {

    Map<String, AttributeValue> values = new HashMap<>();

    values.put(":target", AttributeValue.fromS(target.name()));

    values.put(":orderId", AttributeValue.fromS(orderId));

    String updateExpression;

    if (target == TicketStatus.AVAILABLE) {

      updateExpression = "SET #status = :target " + "REMOVE orderId, reservedUntil";

    } else if (reservedUntil != null) {

      values.put(":reservedUntil", AttributeValue.fromS(reservedUntil.toString()));

      updateExpression =
          "SET #status = :target, " + "orderId = :orderId, " + "reservedUntil = :reservedUntil";

    } else {

      updateExpression = "SET #status = :target, " + "orderId = :orderId " + "REMOVE reservedUntil";
    }

    String conditionExpression;

    Map<String, String> attributeNames = new HashMap<>();

    attributeNames.put("#status", "status");

    if (target == TicketStatus.AVAILABLE) {

      values.put(":reserved", AttributeValue.fromS(TicketStatus.RESERVED.name()));

      values.put(":pendingConfirmation",
          AttributeValue.fromS(TicketStatus.PENDING_CONFIRMATION.name()));

      attributeNames.put("#orderId", "orderId");

      conditionExpression = "(#status = :reserved " + "OR #status = :pendingConfirmation) "
          + "AND #orderId = :orderId";

    } else {

      values.put(":expected", AttributeValue.fromS(expected.name()));

      conditionExpression = "#status = :expected";
    }

    Update update = Update.builder().tableName(DynamoDbTables.TICKETS)
        .key(ticketKey(eventId, ticketId)).updateExpression(updateExpression)
        .conditionExpression(conditionExpression).expressionAttributeNames(attributeNames)
        .expressionAttributeValues(values).build();

    return TransactWriteItem.builder().update(update).build();
  }

  private TransactWriteItem reserveInventoryAction(EventId eventId, int quantity) {

    Map<String, AttributeValue> values = new HashMap<>();

    values.put(":quantity", AttributeValue.fromN(String.valueOf(quantity)));

    Update update = Update.builder().tableName(DynamoDbTables.INVENTORY)
        .key(Map.of("eventId", AttributeValue.fromS(eventId.value())))
        .updateExpression("SET availableTickets = " + "availableTickets - :quantity, " +

            "reservedTickets = " + "reservedTickets + :quantity")
        .conditionExpression("attribute_exists(eventId) " + "AND availableTickets >= :quantity")
        .expressionAttributeValues(values).build();

    return TransactWriteItem.builder().update(update).build();
  }

  private TransactWriteItem confirmInventoryAction(EventId eventId, int quantity) {

    Map<String, AttributeValue> values = Map.of(":q",
        AttributeValue.fromN(String.valueOf(quantity)), ":one", AttributeValue.fromN("1"));

    Update update = Update.builder().tableName(DynamoDbTables.INVENTORY)
        .key(Map.of("eventId", AttributeValue.fromS(eventId.value()))).updateExpression(
            "SET reservedTickets = " + "reservedTickets - :q, " + "soldTickets = "
                + "soldTickets + :q, " + "version = version + :one")
        .conditionExpression("reservedTickets >= :q").expressionAttributeValues(values).build();

    return TransactWriteItem.builder().update(update).build();
  }

  private TransactWriteItem releaseInventoryAction(EventId eventId, int quantity) {

    Map<String, AttributeValue> values = new HashMap<>();

    values.put(":quantity", AttributeValue.fromN(String.valueOf(quantity)));

    Update update = Update.builder().tableName(DynamoDbTables.INVENTORY)
        .key(Map.of("eventId", AttributeValue.fromS(eventId.value())))
        .updateExpression("SET " + "availableTickets = " + "availableTickets + :quantity, " +

            "reservedTickets = " + "reservedTickets - :quantity")
        .conditionExpression("attribute_exists(eventId) " + "AND reservedTickets >= :quantity")
        .expressionAttributeValues(values).build();

    return TransactWriteItem.builder().update(update).build();
  }

  private Map<String, AttributeValue> ticketKey(EventId eventId, TicketId ticketId) {
    return Map.of("eventId", AttributeValue.fromS(eventId.value()), "ticketId",
        AttributeValue.fromS(ticketId.value()));
  }

  private Mono<Void> transact(List<TransactWriteItem> actions) {

    TransactWriteItemsRequest request = TransactWriteItemsRequest.builder().transactItems(actions)
        .build();

    return Mono.fromFuture(client.transactWriteItems(request)).then();
  }

  private Mono<Void> transactInChunks(List<TransactWriteItem> actions) {

    List<Mono<TransactWriteItemsResponse>> calls = new ArrayList<>();

    for (int i = 0; i < actions.size(); i += DYNAMODB_TRANSACTION_LIMIT) {

      List<TransactWriteItem> chunk = actions.subList(i,
          Math.min(i + DYNAMODB_TRANSACTION_LIMIT, actions.size()));

      calls.add(Mono.fromFuture(client.transactWriteItems(
          TransactWriteItemsRequest.builder().transactItems(chunk).build())));
    }

    return Flux.concat(calls).then();
  }

  private Mono<List<Ticket>> findTickets(EventId eventId, List<TicketId> ids) {

    return Flux.fromIterable(ids).flatMap(id -> findOne(eventId, id)).collectList();
  }

  private Mono<Ticket> findOne(EventId eventId, TicketId id) {

    return Mono.fromFuture(client.getItem(
        GetItemRequest.builder().tableName(DynamoDbTables.TICKETS).key(ticketKey(eventId, id))
            .build())).flatMap(
        response -> response.hasItem() ? Mono.just(toDomain(response.item())) : Mono.empty());
  }

  @Override
  public Flux<Ticket> findByEventId(EventId eventId) {

    return Mono.fromFuture(client.query(QueryRequest.builder().tableName(DynamoDbTables.TICKETS)
        .keyConditionExpression("eventId = :eventId")
        .expressionAttributeValues(Map.of(":eventId", AttributeValue.fromS(eventId.value())))
        .build())).flatMapMany(response -> Flux.fromIterable(response.items())).map(this::toDomain);
  }

  @Override
  public Flux<Ticket> findExpired(Instant now) {

    return Mono.fromFuture(client.scan(ScanRequest.builder().tableName(DynamoDbTables.TICKETS)
            .filterExpression("#status = :reserved " + "AND reservedUntil <= :now")
            .expressionAttributeNames(Map.of("#status", "status")).expressionAttributeValues(
                Map.of(":reserved", AttributeValue.fromS(TicketStatus.RESERVED.name()), ":now",
                    AttributeValue.fromS(now.toString()))).build()))
        .flatMapMany(response -> Flux.fromIterable(response.items())).map(this::toDomain);
  }

  private Ticket toDomain(Map<String, AttributeValue> item) {

    return new Ticket(new TicketId(item.get("ticketId").s()), new EventId(item.get("eventId").s()),
        TicketStatus.valueOf(item.get("status").s()),
        item.containsKey("orderId") ? item.get("orderId").s() : null,
        item.containsKey("reservedUntil") ? Instant.parse(item.get("reservedUntil").s()) : null);
  }

  private Mono<List<Ticket>> executeReservationTransaction(EventId eventId,
      List<TicketId> ticketIds, String orderId, Instant reservedUntil) {

    List<TransactWriteItem> actions = new ArrayList<>();

    /*
     * One atomic update per ticket.
     */
    for (TicketId ticketId : ticketIds) {

      actions.add(reserveTicketAction(eventId, ticketId, orderId, reservedUntil));
    }

    /*
     * Inventory participates in the SAME transaction.
     */
    actions.add(reserveInventoryAction(eventId, ticketIds.size()));

    return transact(actions).then(findByIds(eventId, ticketIds))
        .onErrorMap(TransactionCanceledException.class,
            error -> new TicketNotAvailableException("Tickets are no longer available"));
  }

  private TransactWriteItem releaseTicketAction(EventId eventId, TicketId ticketId,
      String orderId) {

    Map<String, AttributeValue> values = new HashMap<>();

    values.put(":reserved", AttributeValue.fromS(TicketStatus.RESERVED.name()));

    values.put(":pending", AttributeValue.fromS(TicketStatus.PENDING_CONFIRMATION.name()));

    values.put(":available", AttributeValue.fromS(TicketStatus.AVAILABLE.name()));

    values.put(":orderId", AttributeValue.fromS(orderId));

    Update update = Update.builder().tableName(DynamoDbTables.TICKETS)
        .key(ticketKey(eventId, ticketId))
        .updateExpression("SET #status = :available " + "REMOVE orderId, reservedUntil")
        .conditionExpression(
            "attribute_exists(eventId) " + "AND (#status = :reserved " + "OR #status = :pending) "
                + "AND orderId = :orderId").expressionAttributeNames(Map.of("#status", "status"))
        .expressionAttributeValues(values).build();

    return TransactWriteItem.builder().update(update).build();
  }

  private Mono<Void> verifyTicketsAreAvailable(List<TicketId> requestedTicketIds,
      List<Ticket> tickets) {

    /*
     * DynamoDB must return exactly the same
     * number of tickets requested.
     */
    if (tickets.size() != requestedTicketIds.size()) {

      return Mono.error(
          new TicketNotAvailableException("One or more requested tickets do not exist"));
    }

    /*
     * EVERY ticket must be AVAILABLE.
     */
    for (Ticket ticket : tickets) {

      if (ticket.status() != TicketStatus.AVAILABLE) {

        return Mono.error(new TicketNotAvailableException(
            "Ticket " + ticket.id().value() + " is not available. " + "Current status: "
                + ticket.status()));
      }
    }

    return Mono.empty();
  }

  private Mono<Ticket> findById(EventId eventId, TicketId ticketId) {

    GetItemRequest request = GetItemRequest.builder().tableName(DynamoDbTables.TICKETS)
        .key(ticketKey(eventId, ticketId)).consistentRead(true).build();

    return Mono.fromFuture(client.getItem(request)).flatMap(response -> {

      if (!response.hasItem()) {
        return Mono.empty();
      }

      return Mono.just(toTicket(response.item()));
    });
  }

  private Mono<Void> verifyInventory(EventId eventId, int quantity) {

    GetItemRequest request = GetItemRequest.builder().tableName(DynamoDbTables.INVENTORY)
        .key(Map.of("eventId", AttributeValue.fromS(eventId.value()))).consistentRead(true).build();

    return Mono.fromFuture(client.getItem(request)).flatMap(response -> {

      if (!response.hasItem()) {

        return Mono.error(new InsufficientInventoryException());
      }

      Map<String, AttributeValue> item = response.item();

      AttributeValue available = item.get("availableTickets");

      if (available == null) {

        return Mono.error(
            new IllegalStateException("Inventory field " + "availableTickets " + "does not exist"));
      }

      int availableTickets = Integer.parseInt(available.n());

      if (availableTickets < quantity) {

        return Mono.error(new InsufficientInventoryException());
      }

      return Mono.empty();
    });
  }

  private TransactWriteItem reserveTicketAction(EventId eventId, TicketId ticketId, String orderId,
      Instant reservedUntil) {

    Map<String, AttributeValue> values = new HashMap<>();

    values.put(":available", AttributeValue.fromS(TicketStatus.AVAILABLE.name()));

    values.put(":reserved", AttributeValue.fromS(TicketStatus.RESERVED.name()));

    values.put(":orderId", AttributeValue.fromS(orderId));

    values.put(":reservedUntil", AttributeValue.fromS(reservedUntil.toString()));

    Update update = Update.builder().tableName(DynamoDbTables.TICKETS)
        .key(ticketKey(eventId, ticketId)).updateExpression(
            "SET #status = :reserved, " + "orderId = :orderId, " + "reservedUntil = :reservedUntil")
        .conditionExpression("attribute_exists(eventId) " + "AND #status = :available")
        .expressionAttributeNames(Map.of("#status", "status")).expressionAttributeValues(values)
        .build();

    return TransactWriteItem.builder().update(update).build();
  }

  private Ticket toTicket(Map<String, AttributeValue> item) {

    AttributeValue eventIdAttribute = item.get("eventId");

    AttributeValue ticketIdAttribute = item.get("ticketId");

    AttributeValue statusAttribute = item.get("status");

    if (eventIdAttribute == null || ticketIdAttribute == null || statusAttribute == null) {

      throw new IllegalStateException("Invalid ticket item in DynamoDB: " + item);
    }

    EventId eventId = new EventId(eventIdAttribute.s());

    TicketId ticketId = new TicketId(ticketIdAttribute.s());

    TicketStatus status = TicketStatus.valueOf(statusAttribute.s());

    String orderId = null;

    AttributeValue orderIdAttribute = item.get("orderId");

    if (orderIdAttribute != null) {
      orderId = orderIdAttribute.s();
    }

    Instant reservedUntil = null;

    AttributeValue reservedUntilAttribute = item.get("reservedUntil");

    if (reservedUntilAttribute != null) {
      reservedUntil = Instant.parse(reservedUntilAttribute.s());
    }

    return new Ticket(ticketId, eventId, status, orderId, reservedUntil);
  }

}