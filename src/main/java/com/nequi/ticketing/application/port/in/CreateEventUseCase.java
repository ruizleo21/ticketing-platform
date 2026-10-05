package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.Event;
import reactor.core.publisher.Mono;

public interface CreateEventUseCase {
    Mono<Event> create(Event event);
}
