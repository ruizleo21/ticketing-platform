package com.nequi.ticketing.infrastructure.adapters.in.web.controller;

import java.util.List;

import com.nequi.ticketing.application.port.in.CreateEventUseCase;
import com.nequi.ticketing.application.port.in.GetAvailabilityUseCase;
import com.nequi.ticketing.application.port.in.GetAvailableEventsUseCase;
import com.nequi.ticketing.application.port.in.GetEventUseCase;
import com.nequi.ticketing.domain.model.Event;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;
import com.nequi.ticketing.domain.model.Ticket;
import com.nequi.ticketing.infrastructure.adapters.in.web.dto.CreateEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final CreateEventUseCase createEventUseCase;
    private final GetEventUseCase getEventUseCase;
    private final GetAvailabilityUseCase getAvailabilityUseCase;
    private final GetAvailableEventsUseCase getAvailableEventsUseCase;

    public EventController(CreateEventUseCase createEventUseCase,
                           GetEventUseCase getEventUseCase,
                           GetAvailabilityUseCase getAvailabilityUseCase,
        GetAvailableEventsUseCase getAvailableEventsUseCase) {
        this.createEventUseCase = createEventUseCase;
        this.getEventUseCase = getEventUseCase;
        this.getAvailabilityUseCase = getAvailabilityUseCase;
        this.getAvailableEventsUseCase = getAvailableEventsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Event> create(@Valid @RequestBody CreateEventRequest request) {
        return createEventUseCase.create(new Event(
                EventId.newId(),
                request.name(),
                request.date(),
                request.venue(),
                request.totalCapacity()
        ));
    }

    @GetMapping("/{eventId}")
    public Mono<Inventory> get(@PathVariable String eventId) {
        return getEventUseCase.getById(new EventId(eventId));
    }

    @GetMapping("/{eventId}/availability")
    public Flux<Ticket> availability(@PathVariable String eventId) {
        return getAvailabilityUseCase.getAvailability(new EventId(eventId));
    }
    @GetMapping("/availables")
    public Mono<List<Event>> getAvailableEvents() {
        return getAvailableEventsUseCase.execute();
    }
}
