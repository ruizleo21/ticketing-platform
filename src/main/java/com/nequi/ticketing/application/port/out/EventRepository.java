package com.nequi.ticketing.application.port.out;

import java.util.List;

import com.nequi.ticketing.domain.model.Event;
import com.nequi.ticketing.domain.model.EventId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventRepository {
    Mono<Event> save(Event event);
    Mono<Event> findById(EventId eventId);
    Flux<Event> findAll();
    Mono<List<Event>> findAvailableEvents();
}
