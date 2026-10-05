package com.nequi.ticketing.domain.exception;

public class TicketNotAvailableException
    extends DomainException {

  public TicketNotAvailableException(
      String message
  ) {
    super(message);
  }
}