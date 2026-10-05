package com.nequi.ticketing.domain.model;

import java.util.Objects;

public record TicketId(String value) {

  public TicketId {
    Objects.requireNonNull(value, "Ticket id cannot be null");

    if (value.isBlank()) {
      throw new IllegalArgumentException("Ticket id cannot be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}