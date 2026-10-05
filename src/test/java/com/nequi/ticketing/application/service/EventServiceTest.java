package com.nequi.ticketing.application.service;

import com.nequi.ticketing.application.port.out.EventRepository;
import com.nequi.ticketing.application.port.out.InventoryRepository;
import com.nequi.ticketing.application.port.out.TicketRepository;
import com.nequi.ticketing.domain.model.Event;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;
import com.nequi.ticketing.domain.model.Ticket;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.domain.model.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  private static final EventId EVENT_ID = new EventId("event-1");
  private static final Instant EVENT_DATE = Instant.parse("2030-06-15T18:00:00Z");

  @Mock
  private EventRepository eventRepository;

  @Mock
  private InventoryRepository inventoryRepository;

  @Mock
  private TicketRepository ticketRepository;

  private EventService eventService;

  @BeforeEach
  void setUp() {
    eventService = new EventService(eventRepository, inventoryRepository, ticketRepository);
  }

  @Test
  void shouldCreateEventInventoryAndTickets() {
    Event event = event(EVENT_ID, 100);
    when(eventRepository.save(event)).thenReturn(Mono.just(event));
    when(inventoryRepository.create(any(Inventory.class))).thenReturn(Mono.empty());
    when(ticketRepository.createForEvent(EVENT_ID, 100)).thenReturn(Mono.empty());

    StepVerifier.create(eventService.create(event))
        .expectNext(event)
        .verifyComplete();

    ArgumentCaptor<Inventory> inventoryCaptor = ArgumentCaptor.forClass(Inventory.class);
    verify(inventoryRepository).create(inventoryCaptor.capture());
    assertEquals(new Inventory(EVENT_ID, 100, 100, 0, 0, 0, 0), inventoryCaptor.getValue());
    verify(ticketRepository).createForEvent(EVENT_ID, 100);
    var order = inOrder(eventRepository, inventoryRepository, ticketRepository);
    order.verify(eventRepository).save(event);
    order.verify(inventoryRepository).create(any(Inventory.class));
    order.verify(ticketRepository).createForEvent(EVENT_ID, 100);
  }

  @Test
  void shouldPropagateErrorWhenSavingEventFails() {
    Event event = event(EVENT_ID, 100);
    RuntimeException error = new RuntimeException("event save failed");
    when(eventRepository.save(event)).thenReturn(Mono.error(error));

    StepVerifier.create(eventService.create(event))
        .expectErrorSatisfies(actual -> assertSame(error, actual))
        .verify();

    verifyNoInteractions(inventoryRepository, ticketRepository);
  }

  @Test
  void shouldPropagateErrorWhenCreatingInventoryFailsWithoutCreatingTickets() {
    Event event = event(EVENT_ID, 100);
    RuntimeException error = new RuntimeException("inventory create failed");
    when(eventRepository.save(event)).thenReturn(Mono.just(event));
    when(inventoryRepository.create(any(Inventory.class))).thenReturn(Mono.error(error));

    StepVerifier.create(eventService.create(event))
        .expectErrorSatisfies(actual -> assertSame(error, actual))
        .verify();

    verify(ticketRepository, never()).createForEvent(any(EventId.class), eq(100));
  }

  @Test
  void shouldPropagateErrorWhenCreatingTicketsFails() {
    Event event = event(EVENT_ID, 100);
    RuntimeException error = new RuntimeException("ticket creation failed");
    when(eventRepository.save(event)).thenReturn(Mono.just(event));
    when(inventoryRepository.create(any(Inventory.class))).thenReturn(Mono.empty());
    when(ticketRepository.createForEvent(EVENT_ID, 100)).thenReturn(Mono.error(error));

    StepVerifier.create(eventService.create(event))
        .expectErrorSatisfies(actual -> assertSame(error, actual))
        .verify();
  }

  @Test
  void shouldGetInventoryByEventId() {
    Inventory inventory = new Inventory(EVENT_ID, 100, 80, 10, 5, 5, 1);
    when(inventoryRepository.findByEventId(EVENT_ID)).thenReturn(Mono.just(inventory));

    StepVerifier.create(eventService.getById(EVENT_ID))
        .expectNext(inventory)
        .verifyComplete();

    verify(inventoryRepository).findByEventId(EVENT_ID);
    verifyNoInteractions(eventRepository, ticketRepository);
  }

  @Test
  void shouldGetTicketAvailabilityByEventId() {
    Ticket available = ticket("ticket-1", TicketStatus.AVAILABLE);
    Ticket reserved = ticket("ticket-2", TicketStatus.RESERVED);
    when(ticketRepository.findByEventId(EVENT_ID)).thenReturn(Flux.just(available, reserved));

    StepVerifier.create(eventService.getAvailability(EVENT_ID))
        .expectNext(available)
        .expectNext(reserved)
        .verifyComplete();

    verify(ticketRepository).findByEventId(EVENT_ID);
    verifyNoInteractions(eventRepository, inventoryRepository);
  }

  @Test
  void shouldReturnOnlyEventsWithAvailableInventory() {
    Event availableEvent = event(new EventId("available-event"), 100);
    Event soldOutEvent = event(new EventId("sold-out-event"), 50);
    Event withoutInventoryEvent = event(new EventId("missing-inventory-event"), 20);
    when(eventRepository.findAvailableEvents())
        .thenReturn(Mono.just(List.of(availableEvent, soldOutEvent, withoutInventoryEvent)));
    when(inventoryRepository.findByEventId(availableEvent.id()))
        .thenReturn(Mono.just(new Inventory(availableEvent.id(), 100, 1, 99, 0, 0, 1)));
    when(inventoryRepository.findByEventId(soldOutEvent.id()))
        .thenReturn(Mono.just(new Inventory(soldOutEvent.id(), 50, 0, 50, 0, 0, 1)));
    when(inventoryRepository.findByEventId(withoutInventoryEvent.id())).thenReturn(Mono.empty());

    StepVerifier.create(eventService.execute())
        .expectNext(List.of(availableEvent))
        .verifyComplete();

    verify(eventRepository).findAvailableEvents();
    verify(inventoryRepository).findByEventId(availableEvent.id());
    verify(inventoryRepository).findByEventId(soldOutEvent.id());
    verify(inventoryRepository).findByEventId(withoutInventoryEvent.id());
    verifyNoInteractions(ticketRepository);
  }

  @Test
  void shouldReturnEmptyListWhenThereAreNoCandidateEvents() {
    when(eventRepository.findAvailableEvents()).thenReturn(Mono.just(List.of()));

    StepVerifier.create(eventService.execute())
        .expectNext(List.of())
        .verifyComplete();

    verify(eventRepository).findAvailableEvents();
    verifyNoInteractions(inventoryRepository, ticketRepository);
  }

  private static Event event(EventId id, int capacity) {
    return new Event(id, "Concert " + id.value(), EVENT_DATE, "Main Hall", capacity);
  }

  private static Ticket ticket(String id, TicketStatus status) {
    return new Ticket(new TicketId(id), EVENT_ID, status, null, null);
  }
}
