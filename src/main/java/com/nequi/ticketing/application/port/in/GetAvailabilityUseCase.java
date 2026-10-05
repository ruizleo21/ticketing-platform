package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Ticket;

import reactor.core.publisher.Flux;

public interface GetAvailabilityUseCase {
    Flux<Ticket> getAvailability(EventId eventId);
}
