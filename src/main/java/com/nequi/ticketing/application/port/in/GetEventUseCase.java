package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;

import reactor.core.publisher.Mono;

public interface GetEventUseCase {
    Mono<Inventory> getById(EventId eventId);
}
