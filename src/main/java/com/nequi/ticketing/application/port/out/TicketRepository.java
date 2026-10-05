package com.nequi.ticketing.application.port.out;

import java.time.Instant;
import java.util.List;

import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Ticket;
import com.nequi.ticketing.domain.model.TicketId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TicketRepository {

  Mono<List<Ticket>> findByIds(
      EventId eventId,
      List<TicketId> ticketIds
  );

  Mono<Void> createForEvent(EventId eventId, int capacity);

  Mono<List<Ticket>> reserve(
      EventId eventId,
      List<TicketId> ticketIds,
      String orderId,
      Instant reservedUntil
  );

  Mono<List<Ticket>> release(
      EventId eventId,
      List<TicketId> ticketIds,
      String orderId
  );

  Mono<List<Ticket>> moveToPendingConfirmation(EventId eventId, List<TicketId> ticketIds,
      String orderId);

  Mono<List<Ticket>> confirmSale(EventId eventId, List<TicketId> ticketIds, String orderId);

  public Flux<Ticket> findByEventId(EventId eventId);

  public Flux<Ticket> findExpired(Instant now);
}