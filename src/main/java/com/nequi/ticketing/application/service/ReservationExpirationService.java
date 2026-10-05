package com.nequi.ticketing.application.service;

import com.nequi.ticketing.application.port.out.OrderRepository;
import com.nequi.ticketing.application.port.out.TicketRepository;
import com.nequi.ticketing.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

import reactor.core.publisher.Mono;

@Component
public class ReservationExpirationService {

    private static final Logger log =
        LoggerFactory.getLogger(
            ReservationExpirationService.class
        );

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final Clock clock;

    public ReservationExpirationService(
        OrderRepository orderRepository,
        TicketRepository ticketRepository,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    @Scheduled(
        fixedDelayString =
            "${ticketing.reservation-expiration.poll-ms:30000}"
    )
    public void releaseExpiredReservations() {

        Instant now = Instant.now(clock);

        log.info(
            "Starting expired reservations release process. executionTime={}",
            now
        );
        orderRepository
            .findExpiredReservations(now)
            .filter(
                order ->
                    order.status() == OrderStatus.RESERVED
                        || order.status()
                        == OrderStatus.PENDING_CONFIRMATION
            ).flatMap(
                order ->
                    ticketRepository
                        .release(
                            order.eventId(),
                            order.ticketIds(),
                            order.id().value()
                        )
                        .then(Mono.defer(() ->
                            orderRepository.save(
                                order.expired(
                                    Instant.now(clock)
                                )
                            )
                        ))
            ).count()
            .doOnSuccess(
                count ->
                    log.info(
                        "Completed expired reservations release process. releasedOrders={}, executionTime={}",
                        count,
                        Instant.now(clock)
                    )
            )
            .doOnError(
                error ->
                    log.error(
                        "Error executing releaseExpiredReservations",
                        error
                    )
            )
            .subscribe();
    }
}