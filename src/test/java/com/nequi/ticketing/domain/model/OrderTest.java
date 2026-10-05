package com.nequi.ticketing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

  private static final OrderId ORDER_ID = new OrderId("order-1");
  private static final EventId EVENT_ID = new EventId("event-1");
  private static final Instant CREATED_AT = Instant.parse("2030-06-15T10:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2030-06-15T11:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2030-06-15T12:00:00Z");
  private static final String IDEMPOTENCY_KEY = "idempotency-1";

  private static Order validOrder() {
    return new Order(
        ORDER_ID,
        EVENT_ID,
        "user-1",
        List.<TicketId>of(new TicketId("ticket-1"), new TicketId("ticket-2")),
        OrderStatus.RESERVED,
        CREATED_AT,
        UPDATED_AT,
        EXPIRES_AT,
        IDEMPOTENCY_KEY
    );
  }

  @Test
  void shouldCreateOrderWithValidValues() {
    Order order = validOrder();

    assertAll(
        () -> assertEquals(ORDER_ID, order.id()),
        () -> assertEquals(EVENT_ID, order.eventId()),
        () -> assertEquals("user-1", order.userId()),
        () -> assertEquals(List.of(new TicketId("ticket-1"), new TicketId("ticket-2")), order.ticketIds()),
        () -> assertEquals(OrderStatus.RESERVED, order.status()),
        () -> assertEquals(CREATED_AT, order.createdAt()),
        () -> assertEquals(UPDATED_AT, order.updatedAt()),
        () -> assertEquals(EXPIRES_AT, order.reservationExpiresAt()),
        () -> assertEquals(IDEMPOTENCY_KEY, order.idempotencyKey()),
        () -> assertEquals(2, order.quantity())
    );
  }

  @Test
  void shouldAcceptOrderWithMinimumTicketCount() {
    Order order = new Order(
        ORDER_ID, EVENT_ID, "user-1", List.<TicketId>of(new TicketId("ticket-1")),
        OrderStatus.RESERVED, CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY
    );

    assertEquals(1, order.quantity());
  }

  @Test
  void shouldAcceptOrderWithMaximumTicketCount() {
    List<TicketId> ticketIds = ticketIds(10);
    Order order = new Order(
        ORDER_ID, EVENT_ID, "user-1", ticketIds,
        OrderStatus.RESERVED, CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY
    );

    assertEquals(10, order.quantity());
  }

  @Test
  void shouldRejectNullTicketList() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Order(ORDER_ID, EVENT_ID, "user-1", null, OrderStatus.RESERVED,
            CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY));

    assertEquals("Ticket count must be between 1 and 10", exception.getMessage());
  }

  @Test
  void shouldRejectEmptyTicketList() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Order(ORDER_ID, EVENT_ID, "user-1", List.of(), OrderStatus.RESERVED,
            CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY));

    assertEquals("Ticket count must be between 1 and 10", exception.getMessage());
  }

  @Test
  void shouldRejectMoreThanTenTickets() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Order(ORDER_ID, EVENT_ID, "user-1", ticketIds(11), OrderStatus.RESERVED,
            CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY));

    assertEquals("Ticket count must be between 1 and 10", exception.getMessage());
  }

  @Test
  void shouldRejectDuplicateTicketIds() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Order(ORDER_ID, EVENT_ID, "user-1",
            List.<TicketId>of(new TicketId("ticket-1"), new TicketId("ticket-1")),
            OrderStatus.RESERVED, CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY));

    assertEquals("Ticket ids must be unique", exception.getMessage());
  }

  @Test
  void shouldCopyTicketListToMakeItImmutable() {
    List<TicketId> mutableTicketIds = new ArrayList<>(ticketIds(2));
    Order order = new Order(
        ORDER_ID, EVENT_ID, "user-1", mutableTicketIds,
        OrderStatus.RESERVED, CREATED_AT, UPDATED_AT, EXPIRES_AT, IDEMPOTENCY_KEY
    );

    mutableTicketIds.add(new TicketId("ticket-3"));

    assertEquals(2, order.ticketIds().size());
    assertThrows(UnsupportedOperationException.class,
        () -> order.ticketIds().add(new TicketId("ticket-4")));
  }

  @Test
  void shouldRejectNullIdempotencyKey() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Order(ORDER_ID, EVENT_ID, "user-1", ticketIds(1), OrderStatus.RESERVED,
            CREATED_AT, UPDATED_AT, EXPIRES_AT, null));

    assertEquals("Idempotency key is required", exception.getMessage());
  }

  @Test
  void shouldRejectBlankIdempotencyKey() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Order(ORDER_ID, EVENT_ID, "user-1", ticketIds(1), OrderStatus.RESERVED,
            CREATED_AT, UPDATED_AT, EXPIRES_AT, "   "));

    assertEquals("Idempotency key is required", exception.getMessage());
  }

  @Test
  void shouldCreatePendingConfirmationOrderWithUpdatedTimestamp() {
    Order original = validOrder();

    Order updated = original.pendingConfirmation(UPDATED_AT.plusSeconds(60));

    assertAll(
        () -> assertEquals(ORDER_ID, updated.id()),
        () -> assertEquals(EVENT_ID, updated.eventId()),
        () -> assertEquals("user-1", updated.userId()),
        () -> assertEquals(original.ticketIds(), updated.ticketIds()),
        () -> assertEquals(OrderStatus.PENDING_CONFIRMATION, updated.status()),
        () -> assertEquals(CREATED_AT, updated.createdAt()),
        () -> assertEquals(UPDATED_AT.plusSeconds(60), updated.updatedAt()),
        () -> assertEquals(EXPIRES_AT, updated.reservationExpiresAt()),
        () -> assertEquals(IDEMPOTENCY_KEY, updated.idempotencyKey())
    );
  }

  @Test
  void shouldCreateSoldOrderAndClearReservationExpiration() {
    Order original = validOrder();

    Order updated = original.sold(UPDATED_AT.plusSeconds(60));

    assertAll(
        () -> assertEquals(OrderStatus.SOLD, updated.status()),
        () -> assertEquals(UPDATED_AT.plusSeconds(60), updated.updatedAt()),
        () -> assertNull(updated.reservationExpiresAt()),
        () -> assertEquals(CREATED_AT, updated.createdAt()),
        () -> assertEquals(original.ticketIds(), updated.ticketIds()),
        () -> assertEquals(IDEMPOTENCY_KEY, updated.idempotencyKey())
    );
  }

  @Test
  void shouldCreateExpiredOrderWithUpdatedTimestamp() {
    Order original = validOrder();

    Order updated = original.expired(UPDATED_AT.plusSeconds(60));

    assertAll(
        () -> assertEquals(OrderStatus.EXPIRED, updated.status()),
        () -> assertEquals(UPDATED_AT.plusSeconds(60), updated.updatedAt()),
        () -> assertEquals(EXPIRES_AT, updated.reservationExpiresAt()),
        () -> assertEquals(CREATED_AT, updated.createdAt()),
        () -> assertEquals(original.ticketIds(), updated.ticketIds()),
        () -> assertEquals(IDEMPOTENCY_KEY, updated.idempotencyKey())
    );
  }

  @Test
  void shouldImplementRecordEqualityHashCodeAndStringRepresentation() {
    Order order = validOrder();
    Order sameOrder = validOrder();
    Order differentOrder = order.sold(UPDATED_AT);

    assertEquals(order, sameOrder);
    assertEquals(order.hashCode(), sameOrder.hashCode());
    assertNotEquals(order, differentOrder);
    assertTrue(order.toString().contains("idempotencyKey=" + IDEMPOTENCY_KEY));
  }

  private static List<TicketId> ticketIds(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(index -> new TicketId("ticket-" + index))
        .toList();
  }
}
