package com.nequi.ticketing.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.nequi.ticketing.application.port.in.CreateEventUseCase;
import com.nequi.ticketing.application.port.in.GetAvailabilityUseCase;
import com.nequi.ticketing.application.port.in.GetAvailableEventsUseCase;
import com.nequi.ticketing.application.port.in.GetEventUseCase;
import com.nequi.ticketing.application.port.out.EventRepository;
import com.nequi.ticketing.application.port.out.InventoryRepository;
import com.nequi.ticketing.application.port.out.TicketRepository;
import com.nequi.ticketing.domain.model.Event;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;
import com.nequi.ticketing.domain.model.Ticket;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EventService implements CreateEventUseCase, GetEventUseCase, GetAvailabilityUseCase,
    GetAvailableEventsUseCase {

  private final EventRepository eventRepository;
  private final InventoryRepository inventoryRepository;
  private final TicketRepository ticketRepository;

  public EventService(EventRepository eventRepository, InventoryRepository inventoryRepository,
      TicketRepository ticketRepository) {
    this.eventRepository = eventRepository;
    this.inventoryRepository = inventoryRepository;
    this.ticketRepository = ticketRepository;
  }

  @Override
  public Mono<Event> create(Event event) {

    return eventRepository.save(event).flatMap(saved -> inventoryRepository.create(
            new Inventory(saved.id(), saved.totalCapacity(), saved.totalCapacity(), 0, 0, 0, 0))
        .then(Mono.defer(() ->ticketRepository.createForEvent(saved.id(), saved.totalCapacity())))
        .thenReturn(saved));
  }

  @Override
  public Mono<Inventory> getById(EventId eventId) {

    return inventoryRepository.findByEventId(eventId);
  }

  @Override
  public Flux<Ticket> getAvailability(EventId eventId) {
    return ticketRepository.findByEventId(eventId);
  }

  @Override
  public Mono<List<Event>> execute() {
    return eventRepository.findAvailableEvents()
        .flatMapMany(Flux::fromIterable)
        .concatMap(event -> inventoryRepository.findByEventId(event.id())
            .filter(inventory -> inventory.availableTickets() > 0)
            .map(inventory -> event)
        ).collectList();
  }
}