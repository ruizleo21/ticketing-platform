package com.nequi.ticketing.infrastructure.adapters.in.web.controller;

import com.nequi.ticketing.application.port.in.CreateEventUseCase;
import com.nequi.ticketing.application.port.in.GetAvailabilityUseCase;
import com.nequi.ticketing.application.port.in.GetAvailableEventsUseCase;
import com.nequi.ticketing.application.port.in.GetEventUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

  private static final String EVENT_ID_VALUE = "event-1";
  private static final Instant EVENT_DATE = Instant.parse("2030-06-15T18:00:00Z");

  @Mock
  private CreateEventUseCase createEventUseCase;

  @Mock
  private GetEventUseCase getEventUseCase;

  @Mock
  private GetAvailabilityUseCase getAvailabilityUseCase;

  @Mock
  private GetAvailableEventsUseCase getAvailableEventsUseCase;

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    EventController controller = new EventController(
        createEventUseCase,
        getEventUseCase,
        getAvailabilityUseCase,
        getAvailableEventsUseCase
    );
    webTestClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void shouldCreateEventThroughHttpAndReturnCreated() {
    when(createEventUseCase.create(any(Event.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    webTestClient.post()
        .uri("/api/v1/events")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(createEventJson())
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.id.value").isNotEmpty()
        .jsonPath("$.name").isEqualTo("Concert")
        .jsonPath("$.date").isEqualTo(EVENT_DATE.toString())
        .jsonPath("$.venue").isEqualTo("Main Hall")
        .jsonPath("$.totalCapacity").isEqualTo(100);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(createEventUseCase).create(eventCaptor.capture());
    Event created = eventCaptor.getValue();
    assertEquals("Concert", created.name());
    assertEquals(EVENT_DATE, created.date());
    assertEquals("Main Hall", created.venue());
    assertEquals(100, created.totalCapacity());
    verifyNoInteractions(getEventUseCase, getAvailabilityUseCase, getAvailableEventsUseCase);
  }

  @Test
  void shouldGetEventInventoryThroughHttp() {
    EventId eventId = new EventId(EVENT_ID_VALUE);
    Inventory inventory = new Inventory(eventId, 100, 75, 10, 10, 5, 1);
    when(getEventUseCase.getById(eventId)).thenReturn(Mono.just(inventory));

    webTestClient.get()
        .uri("/api/v1/events/{eventId}", EVENT_ID_VALUE)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.eventId.value").isEqualTo(EVENT_ID_VALUE)
        .jsonPath("$.totalTickets").isEqualTo(100)
        .jsonPath("$.availableTickets").isEqualTo(75)
        .jsonPath("$.reservedTickets").isEqualTo(10)
        .jsonPath("$.soldTickets").isEqualTo(10)
        .jsonPath("$.complimentaryTickets").isEqualTo(5)
        .jsonPath("$.version").isEqualTo(1);

    verify(getEventUseCase).getById(eventId);
    verifyNoInteractions(createEventUseCase, getAvailabilityUseCase, getAvailableEventsUseCase);
  }

  @Test
  void shouldGetTicketAvailabilityThroughHttp() {
    EventId eventId = new EventId(EVENT_ID_VALUE);
    Ticket available = new Ticket(
        new TicketId("ticket-1"), eventId, TicketStatus.AVAILABLE, null, null);
    Ticket reserved = new Ticket(
        new TicketId("ticket-2"), eventId, TicketStatus.RESERVED,
        "order-1", EVENT_DATE.plusSeconds(600));
    when(getAvailabilityUseCase.getAvailability(eventId))
        .thenReturn(Flux.just(available, reserved));

    webTestClient.get()
        .uri("/api/v1/events/{eventId}/availability", EVENT_ID_VALUE)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].id.value").isEqualTo("ticket-1")
        .jsonPath("$[0].status").isEqualTo("AVAILABLE")
        .jsonPath("$[1].id.value").isEqualTo("ticket-2")
        .jsonPath("$[1].status").isEqualTo("RESERVED")
        .jsonPath("$[1].orderId").isEqualTo("order-1");

    verify(getAvailabilityUseCase).getAvailability(eventId);
    verifyNoInteractions(createEventUseCase, getEventUseCase, getAvailableEventsUseCase);
  }

  @Test
  void shouldGetAvailableEventsThroughHttp() {
    Event event = new Event(
        new EventId(EVENT_ID_VALUE), "Concert", EVENT_DATE, "Main Hall", 100);
    when(getAvailableEventsUseCase.execute()).thenReturn(Mono.just(List.of(event)));

    webTestClient.get()
        .uri("/api/v1/events/availables")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].id.value").isEqualTo(EVENT_ID_VALUE)
        .jsonPath("$[0].name").isEqualTo("Concert")
        .jsonPath("$[0].date").isEqualTo(EVENT_DATE.toString())
        .jsonPath("$[0].venue").isEqualTo("Main Hall")
        .jsonPath("$[0].totalCapacity").isEqualTo(100);

    verify(getAvailableEventsUseCase).execute();
    verifyNoInteractions(createEventUseCase, getEventUseCase, getAvailabilityUseCase);
  }

  @Test
  void shouldReturnEmptyArrayWhenThereAreNoAvailableEvents() {
    when(getAvailableEventsUseCase.execute()).thenReturn(Mono.just(List.of()));

    webTestClient.get()
        .uri("/api/v1/events/availables")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .json("[]");

    verify(getAvailableEventsUseCase).execute();
    verifyNoInteractions(createEventUseCase, getEventUseCase, getAvailabilityUseCase);
  }

  private static Map<String, Object> createEventJson() {
    return Map.of(
        "name", "Concert",
        "date", EVENT_DATE.toString(),
        "venue", "Main Hall",
        "totalCapacity", 100
    );
  }
}
