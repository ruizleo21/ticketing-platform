package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.Event;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GetAvailableEventsUseCase {

  Mono<List<Event>> execute();
}