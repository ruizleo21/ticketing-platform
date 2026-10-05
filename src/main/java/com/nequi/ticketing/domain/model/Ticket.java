package com.nequi.ticketing.domain.model;

import java.time.Instant;

public record Ticket(
    TicketId id,
    EventId eventId,
    TicketStatus status,
    String orderId,
    Instant reservedUntil
) {

  public Ticket reserve(
      String orderId,
      Instant reservedUntil
  ) {
    if (status != TicketStatus.AVAILABLE) {
      throw new IllegalStateException(
          "Ticket " + id.value() + " is not available"
      );
    }

    return new Ticket(
        id,
        eventId,
        TicketStatus.RESERVED,
        orderId,
        reservedUntil
    );
  }

  public Ticket pendingConfirmation() {
    if (status != TicketStatus.RESERVED) {
      throw new IllegalStateException(
          "Ticket " + id.value() + " is not reserved"
      );
    }

    return new Ticket(
        id,
        eventId,
        TicketStatus.PENDING_CONFIRMATION,
        orderId,
        reservedUntil
    );
  }

  public Ticket sold() {
    if (status != TicketStatus.PENDING_CONFIRMATION
        && status != TicketStatus.RESERVED) {
      throw new IllegalStateException(
          "Ticket " + id.value() + " cannot be sold"
      );
    }

    return new Ticket(
        id,
        eventId,
        TicketStatus.SOLD,
        orderId,
        null
    );
  }

  public Ticket release() {
    if (status != TicketStatus.RESERVED
        && status != TicketStatus.PENDING_CONFIRMATION) {
      throw new IllegalStateException(
          "Ticket " + id.value() + " cannot be released"
      );
    }

    return new Ticket(
        id,
        eventId,
        TicketStatus.AVAILABLE,
        null,
        null
    );
  }
}