package com.nequi.ticketing.application.service;

import com.nequi.ticketing.application.port.out.OrderRepository;
import com.nequi.ticketing.application.port.out.TicketRepository;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

  private static final Instant NOW = Instant.parse("2030-06-15T10:00:00Z");
  private static final EventId EVENT_ID = new EventId("event-1");
  private static final List<TicketId> TICKET_IDS = List.<TicketId>of(
      new TicketId("ticket-1"), new TicketId("ticket-2"));

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private TicketRepository ticketRepository;

  private ReservationExpirationService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new ReservationExpirationService(orderRepository, ticketRepository, clock);
  }

  @Test
  void shouldReleaseAndExpireReservedAndPendingConfirmationOrders() {
    Order reserved = order("order-reserved", OrderStatus.RESERVED);
    Order pending = order("order-pending", OrderStatus.PENDING_CONFIRMATION);
    when(orderRepository.findExpiredReservations(NOW))
        .thenReturn(Flux.just(reserved, pending));
    when(ticketRepository.release(eq(EVENT_ID), eq(TICKET_IDS), anyString()))
        .thenReturn(Mono.empty());
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    service.releaseExpiredReservations();

    verify(orderRepository).findExpiredReservations(NOW);
    verify(ticketRepository).release(EVENT_ID, TICKET_IDS, "order-reserved");
    verify(ticketRepository).release(EVENT_ID, TICKET_IDS, "order-pending");

    ArgumentCaptor<Order> savedOrders = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, org.mockito.Mockito.times(2)).save(savedOrders.capture());
    assertEquals(2, savedOrders.getAllValues().size());
    savedOrders.getAllValues().forEach(saved -> {
      assertEquals(OrderStatus.EXPIRED, saved.status());
      assertEquals(NOW, saved.updatedAt());
    });
  }

  @Test
  void shouldIgnoreOrdersThatAreNotReservedOrPendingConfirmation() {
    Order sold = order("order-sold", OrderStatus.SOLD);
    Order expired = order("order-expired", OrderStatus.EXPIRED);
    when(orderRepository.findExpiredReservations(NOW))
        .thenReturn(Flux.just(sold, expired));

    service.releaseExpiredReservations();

    verify(orderRepository).findExpiredReservations(NOW);
    verifyNoInteractions(ticketRepository);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldCompleteWhenThereAreNoExpiredOrders() {
    when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.empty());

    service.releaseExpiredReservations();

    verify(orderRepository).findExpiredReservations(NOW);
    verifyNoInteractions(ticketRepository);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotSaveOrderWhenTicketReleaseFails() {
    Order reserved = order("order-reserved", OrderStatus.RESERVED);
    RuntimeException releaseError = new RuntimeException("release failed");
    when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.just(reserved));
    when(ticketRepository.release(EVENT_ID, TICKET_IDS, "order-reserved"))
        .thenReturn(Mono.error(releaseError));
    service.releaseExpiredReservations();
    verify(ticketRepository).release(EVENT_ID, TICKET_IDS, "order-reserved");
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldHandleErrorFindingExpiredReservations() {
    RuntimeException queryError = new RuntimeException("query failed");
    when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.error(queryError));

    // The service subscribes internally; the error is logged and is not thrown to the caller.
    service.releaseExpiredReservations();

    verify(orderRepository).findExpiredReservations(NOW);
    verifyNoInteractions(ticketRepository);
    verify(orderRepository, never()).save(any(Order.class));
  }

  private static Order order(String id, OrderStatus status) {
    Instant expiresAt = NOW.minusSeconds(60);
    return new Order(
        new OrderId(id),
        EVENT_ID,
        "user-1",
        TICKET_IDS,
        status,
        NOW.minusSeconds(600),
        NOW.minusSeconds(300),
        expiresAt,
        "idem-" + id
    );
  }
}
