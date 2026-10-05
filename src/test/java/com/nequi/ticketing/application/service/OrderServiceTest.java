package com.nequi.ticketing.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nequi.ticketing.application.port.out.OrderPublisher;
import com.nequi.ticketing.application.port.out.OrderRepository;
import com.nequi.ticketing.application.port.out.TicketRepository;
import com.nequi.ticketing.domain.exception.InsufficientInventoryException;
import com.nequi.ticketing.domain.exception.OrderNotFoundException;
import com.nequi.ticketing.domain.exception.TicketNotAvailableException;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2030-06-15T10:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(600);
    private static final OrderId ORDER_ID = new OrderId("order-1");
    private static final EventId EVENT_ID = new EventId("event-1");
    private static final List<TicketId> TICKET_IDS = List.<TicketId>of(
        new TicketId("ticket-1"), new TicketId("ticket-2"));
    private static final String USER_ID = "user-1";
    private static final String IDEMPOTENCY_KEY = "idem-1";

    @Mock private TicketRepository ticketRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderPublisher orderPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        orderService = new OrderService(ticketRepository, orderRepository, orderPublisher, clock);
    }

    @Test
    void shouldReturnExistingOrderForIdempotencyKeyWithoutReservingOrPublishing() {
        Order existing = order(OrderStatus.RESERVED);
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.just(existing));

        StepVerifier.create(orderService.execute("event-1", USER_ID, TICKET_IDS, IDEMPOTENCY_KEY))
            .expectNext(existing)
            .verifyComplete();

        verify(orderRepository).findByIdempotencyKey(IDEMPOTENCY_KEY);
        verifyNoInteractions(ticketRepository, orderPublisher);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReserveSaveAndPublishNewOrder() {
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(ticketRepository.reserve(eq(EVENT_ID), eq(TICKET_IDS), anyString(), eq(EXPIRES_AT)))
            .thenReturn(Mono.just(List.of()));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(Order.class))).thenReturn(Mono.empty());

        StepVerifier.create(orderService.execute("event-1", USER_ID, TICKET_IDS, IDEMPOTENCY_KEY))
            .assertNext(order -> {
                assertEquals(EVENT_ID, order.eventId());
                assertEquals(USER_ID, order.userId());
                assertEquals(TICKET_IDS, order.ticketIds());
                assertEquals(OrderStatus.RESERVED, order.status());
                assertEquals(NOW, order.createdAt());
                assertEquals(NOW, order.updatedAt());
                assertEquals(EXPIRES_AT, order.reservationExpiresAt());
                assertEquals(IDEMPOTENCY_KEY, order.idempotencyKey());
            })
            .verifyComplete();

        verify(ticketRepository).reserve(eq(EVENT_ID), eq(TICKET_IDS), anyString(), eq(EXPIRES_AT));
        verify(orderRepository).save(any(Order.class));
        verify(orderPublisher).publish(any(Order.class));
        verify(ticketRepository, never()).release(any(), anyList(), anyString());
    }

    @Test
    void shouldNotSaveOrReleaseWhenTicketIsUnavailable() {
        TicketNotAvailableException error = new TicketNotAvailableException("unavailable");
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(ticketRepository.reserve(eq(EVENT_ID), eq(TICKET_IDS), anyString(), eq(EXPIRES_AT)))
            .thenReturn(Mono.error(error));

        StepVerifier.create(orderService.execute("event-1", USER_ID, TICKET_IDS, IDEMPOTENCY_KEY))
            .expectErrorSatisfies(actual -> assertSame(error, actual))
            .verify();

        verify(orderRepository, never()).save(any());
        verify(ticketRepository, never()).release(any(), anyList(), anyString());
        verifyNoInteractions(orderPublisher);
    }

    @Test
    void shouldNotSaveOrReleaseWhenInventoryIsInsufficient() {
        InsufficientInventoryException error = new InsufficientInventoryException();
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(ticketRepository.reserve(eq(EVENT_ID), eq(TICKET_IDS), anyString(), eq(EXPIRES_AT)))
            .thenReturn(Mono.error(error));

        StepVerifier.create(orderService.execute("event-1", USER_ID, TICKET_IDS, IDEMPOTENCY_KEY))
            .expectErrorSatisfies(actual -> assertSame(error, actual))
            .verify();

        verify(orderRepository, never()).save(any());
        verify(ticketRepository, never()).release(any(), anyList(), anyString());
        verifyNoInteractions(orderPublisher);
    }

    @Test
    void shouldReleaseReservationAndPropagateErrorWhenSavingOrderFails() {
        RuntimeException saveError = new RuntimeException("database unavailable");
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(ticketRepository.reserve(eq(EVENT_ID), eq(TICKET_IDS), anyString(), eq(EXPIRES_AT)))
            .thenReturn(Mono.just(List.of()));
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.error(saveError));
        when(ticketRepository.release(eq(EVENT_ID), eq(TICKET_IDS), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(orderService.execute("event-1", USER_ID, TICKET_IDS, IDEMPOTENCY_KEY))
            .expectErrorSatisfies(actual -> assertSame(saveError, actual))
            .verify();

        verify(ticketRepository).release(eq(EVENT_ID), eq(TICKET_IDS), anyString());
        verifyNoInteractions(orderPublisher);
    }

    @Test
    void shouldPropagateUnexpectedReservationErrorAfterAttemptingRelease() {
        RuntimeException reserveError = new RuntimeException("reservation infrastructure failure");
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(ticketRepository.reserve(eq(EVENT_ID), eq(TICKET_IDS), anyString(), eq(EXPIRES_AT)))
            .thenReturn(Mono.error(reserveError));
        when(ticketRepository.release(eq(EVENT_ID), eq(TICKET_IDS), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(orderService.execute("event-1", USER_ID, TICKET_IDS, IDEMPOTENCY_KEY))
            .expectErrorSatisfies(actual -> assertSame(reserveError, actual))
            .verify();

        verify(ticketRepository).release(eq(EVENT_ID), eq(TICKET_IDS), anyString());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderPublisher);
    }

    @Test
    void shouldFindOrderById() {
        Order expected = order(OrderStatus.RESERVED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(expected));

        StepVerifier.create(orderService.execute(ORDER_ID))
            .expectNext(expected)
            .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenProcessingOrderDoesNotFindCurrentOrder() {
        Order supplied = order(OrderStatus.RESERVED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(orderService.execute(supplied)).verifyComplete();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReturnSoldOrderUnchangedWhenProcessing() {
        Order sold = order(OrderStatus.SOLD);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(sold));

        StepVerifier.create(orderService.execute(order(OrderStatus.RESERVED)))
            .expectNext(sold)
            .verifyComplete();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReturnExpiredOrderUnchangedWhenProcessing() {
        Order expired = order(OrderStatus.EXPIRED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(expired));

        StepVerifier.create(orderService.execute(order(OrderStatus.RESERVED)))
            .expectNext(expired)
            .verifyComplete();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReturnPendingConfirmationOrderUnchangedWhenProcessing() {
        Order pending = order(OrderStatus.PENDING_CONFIRMATION);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pending));

        StepVerifier.create(orderService.execute(order(OrderStatus.RESERVED)))
            .expectNext(pending)
            .verifyComplete();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldMoveReservedOrderToPendingConfirmationWhenProcessing() {
        Order reserved = order(OrderStatus.RESERVED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(reserved));
        when(ticketRepository.moveToPendingConfirmation(EVENT_ID, TICKET_IDS, ORDER_ID.value()))
            .thenReturn(Mono.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(orderService.execute(reserved))
            .assertNext(updated -> {
                assertEquals(OrderStatus.PENDING_CONFIRMATION, updated.status());
                assertEquals(NOW, updated.updatedAt());
                assertEquals(reserved.reservationExpiresAt(), updated.reservationExpiresAt());
            })
            .verifyComplete();

        verify(ticketRepository).moveToPendingConfirmation(EVENT_ID, TICKET_IDS, ORDER_ID.value());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, captor.getValue().status());
    }

    @Test
    void shouldFailConfirmationWhenOrderDoesNotExist() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(orderService.confirm(ORDER_ID))
            .expectErrorSatisfies(error -> assertInstanceOf(OrderNotFoundException.class, error))
            .verify();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReturnSoldOrderWithoutConfirmingAgain() {
        Order sold = order(OrderStatus.SOLD);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(sold));

        StepVerifier.create(orderService.confirm(ORDER_ID))
            .expectNext(sold)
            .verifyComplete();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReturnExpiredOrderWithoutConfirming() {
        Order expired = order(OrderStatus.EXPIRED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(expired));

        StepVerifier.create(orderService.confirm(ORDER_ID))
            .expectNext(expired)
            .verifyComplete();

        verifyNoInteractions(ticketRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldMoveReservedTicketsThenConfirmSaleAndSaveSoldOrder() {
        Order reserved = order(OrderStatus.RESERVED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(reserved));
        when(ticketRepository.moveToPendingConfirmation(EVENT_ID, TICKET_IDS, ORDER_ID.value()))
            .thenReturn(Mono.empty());
        when(ticketRepository.confirmSale(EVENT_ID, TICKET_IDS, ORDER_ID.value()))
            .thenReturn(Mono.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(orderService.confirm(ORDER_ID))
            .assertNext(updated -> {
                assertEquals(OrderStatus.SOLD, updated.status());
                assertEquals(NOW, updated.updatedAt());
                assertNull(updated.reservationExpiresAt());
            })
            .verifyComplete();

        verify(ticketRepository).moveToPendingConfirmation(EVENT_ID, TICKET_IDS, ORDER_ID.value());
        verify(ticketRepository).confirmSale(EVENT_ID, TICKET_IDS, ORDER_ID.value());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldConfirmSaleDirectlyWhenOrderIsAlreadyPendingConfirmation() {
        Order pending = order(OrderStatus.PENDING_CONFIRMATION);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pending));
        when(ticketRepository.confirmSale(EVENT_ID, TICKET_IDS, ORDER_ID.value()))
            .thenReturn(Mono.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(orderService.confirm(ORDER_ID))
            .assertNext(updated -> {
                assertEquals(OrderStatus.SOLD, updated.status());
                assertNull(updated.reservationExpiresAt());
            })
            .verifyComplete();

        verify(ticketRepository, never()).moveToPendingConfirmation(any(), anyList(), anyString());
        verify(ticketRepository).confirmSale(EVENT_ID, TICKET_IDS, ORDER_ID.value());
    }

    private static Order order(OrderStatus status) {
        return new Order(ORDER_ID, EVENT_ID, USER_ID, TICKET_IDS, status,
            NOW.minusSeconds(60), NOW.minusSeconds(30), EXPIRES_AT, IDEMPOTENCY_KEY);
    }
}
