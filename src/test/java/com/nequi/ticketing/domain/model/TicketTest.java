package com.nequi.ticketing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {

  private static final TicketId TICKET_ID = new TicketId("ticket-1");
  private static final EventId EVENT_ID = new EventId("event-1");
  private static final String ORDER_ID = "order-1";
  private static final Instant RESERVED_UNTIL = Instant.parse("2030-06-15T12:00:00Z");

  private static Ticket availableTicket() {
    return new Ticket(TICKET_ID, EVENT_ID, TicketStatus.AVAILABLE, null, null);
  }

  private static Ticket reservedTicket() {
    return availableTicket().reserve(ORDER_ID, RESERVED_UNTIL);
  }

  private static Ticket pendingConfirmationTicket() {
    return reservedTicket().pendingConfirmation();
  }

  @Test
  void shouldReserveAvailableTicket() {
    Ticket ticket = availableTicket().reserve(ORDER_ID, RESERVED_UNTIL);

    assertAll(
        () -> assertEquals(TICKET_ID, ticket.id()),
        () -> assertEquals(EVENT_ID, ticket.eventId()),
        () -> assertEquals(TicketStatus.RESERVED, ticket.status()),
        () -> assertEquals(ORDER_ID, ticket.orderId()),
        () -> assertEquals(RESERVED_UNTIL, ticket.reservedUntil())
    );
  }

  @Test
  void shouldRejectReservingTicketThatIsNotAvailable() {
    Ticket ticket = reservedTicket();

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> ticket.reserve("another-order", RESERVED_UNTIL));

    assertEquals("Ticket ticket-1 is not available", exception.getMessage());
  }

  @Test
  void shouldMoveReservedTicketToPendingConfirmation() {
    Ticket ticket = reservedTicket().pendingConfirmation();

    assertAll(
        () -> assertEquals(TICKET_ID, ticket.id()),
        () -> assertEquals(EVENT_ID, ticket.eventId()),
        () -> assertEquals(TicketStatus.PENDING_CONFIRMATION, ticket.status()),
        () -> assertEquals(ORDER_ID, ticket.orderId()),
        () -> assertEquals(RESERVED_UNTIL, ticket.reservedUntil())
    );
  }

  @Test
  void shouldRejectPendingConfirmationWhenTicketIsNotReserved() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> availableTicket().pendingConfirmation());

    assertEquals("Ticket ticket-1 is not reserved", exception.getMessage());
  }

  @Test
  void shouldSellReservedTicketAndClearReservationExpiration() {
    Ticket ticket = reservedTicket().sold();

    assertAll(
        () -> assertEquals(TicketStatus.SOLD, ticket.status()),
        () -> assertEquals(ORDER_ID, ticket.orderId()),
        () -> assertNull(ticket.reservedUntil())
    );
  }

  @Test
  void shouldSellPendingConfirmationTicketAndClearReservationExpiration() {
    Ticket ticket = pendingConfirmationTicket().sold();

    assertAll(
        () -> assertEquals(TicketStatus.SOLD, ticket.status()),
        () -> assertEquals(ORDER_ID, ticket.orderId()),
        () -> assertNull(ticket.reservedUntil())
    );
  }

  @Test
  void shouldRejectSellingTicketInOtherStatus() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> availableTicket().sold());

    assertEquals("Ticket ticket-1 cannot be sold", exception.getMessage());
  }

  @Test
  void shouldReleaseReservedTicket() {
    Ticket ticket = reservedTicket().release();

    assertAll(
        () -> assertEquals(TICKET_ID, ticket.id()),
        () -> assertEquals(EVENT_ID, ticket.eventId()),
        () -> assertEquals(TicketStatus.AVAILABLE, ticket.status()),
        () -> assertNull(ticket.orderId()),
        () -> assertNull(ticket.reservedUntil())
    );
  }

  @Test
  void shouldReleasePendingConfirmationTicket() {
    Ticket ticket = pendingConfirmationTicket().release();

    assertAll(
        () -> assertEquals(TicketStatus.AVAILABLE, ticket.status()),
        () -> assertNull(ticket.orderId()),
        () -> assertNull(ticket.reservedUntil())
    );
  }

  @Test
  void shouldRejectReleasingTicketInOtherStatus() {
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> availableTicket().release());

    assertEquals("Ticket ticket-1 cannot be released", exception.getMessage());
  }

  @Test
  void shouldImplementRecordEqualityHashCodeAndStringRepresentation() {
    Ticket ticket = reservedTicket();
    Ticket sameTicket = reservedTicket();
    Ticket differentTicket = pendingConfirmationTicket();

    assertEquals(ticket, sameTicket);
    assertEquals(ticket.hashCode(), sameTicket.hashCode());
    assertNotEquals(ticket, differentTicket);
    assertTrue(ticket.toString().contains("status=RESERVED"));
  }
}
