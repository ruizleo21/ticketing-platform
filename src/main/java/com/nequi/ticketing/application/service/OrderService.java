package com.nequi.ticketing.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nequi.ticketing.application.port.in.ConfirmOrderUseCase;
import com.nequi.ticketing.application.port.in.CreateOrderUseCase;
import com.nequi.ticketing.application.port.in.GetOrderStatusUseCase;
import com.nequi.ticketing.application.port.in.ProcessOrderUseCase;
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

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class OrderService implements CreateOrderUseCase, GetOrderStatusUseCase, ProcessOrderUseCase,
    ConfirmOrderUseCase {

  private final TicketRepository ticketRepository;
  private final OrderRepository orderRepository;
  private final OrderPublisher orderPublisher;
  private final Clock clock;

  public OrderService(TicketRepository ticketRepository, OrderRepository orderRepository,
      OrderPublisher orderPublisher, Clock clock) {
    this.ticketRepository = ticketRepository;
    this.orderRepository = orderRepository;
    this.orderPublisher = orderPublisher;
    this.clock = clock;
  }

  @Override
  public Mono<Order> execute(String eventId, String userId, List<TicketId> ticketIds,
      String idempotencyKey) {

    return orderRepository.findByIdempotencyKey(idempotencyKey)
        .switchIfEmpty(Mono.defer(() -> createOrder(eventId, userId, ticketIds, idempotencyKey)));
  }

  private Mono<Order> createOrder(String eventId, String userId, List<TicketId> ticketIds,
      String idempotencyKey) {

    Instant now = Instant.now(clock);

    Instant expiresAt = now.plus(Duration.ofMinutes(10));

    Order order = new Order(OrderId.newId(), new EventId(eventId), userId, ticketIds,
        OrderStatus.RESERVED, now, now, expiresAt, idempotencyKey);

    /*
     * NOTHING is saved before reserve()
     * succeeds.
     */
    return ticketRepository.reserve(order.eventId(), order.ticketIds(), order.id().value(),
            expiresAt)

        /*
         * This executes ONLY after reservation
         * was successful.
         */.then(Mono.defer(() -> orderRepository.save(order)))

        /*
         * If the reservation succeeded but saving
         * the order failed, rollback the reservation.
         */.onErrorResume(error -> {

          if (error instanceof TicketNotAvailableException
              || error instanceof InsufficientInventoryException) {

            return Mono.error(error);
          }

          return ticketRepository.release(order.eventId(), order.ticketIds(), order.id().value())
              .then(Mono.error(error));
        })

        .flatMap(saved -> orderPublisher.publish(saved).thenReturn(saved));
  }

  @Override
  public Mono<Order> execute(OrderId orderId) {

    return orderRepository.findById(orderId)
        .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId.value())));
  }

  @Override
  public Mono<Order> execute(Order order) {

    return orderRepository.findById(order.id()).flatMap(current -> {

      if (current.status() == OrderStatus.SOLD || current.status() == OrderStatus.EXPIRED) {

        return Mono.just(current);
      }

      if (current.status() == OrderStatus.PENDING_CONFIRMATION) {

        return Mono.just(current);
      }

      return ticketRepository.moveToPendingConfirmation(current.eventId(), current.ticketIds(),
              current.id().value())
          .then(orderRepository.save(current.pendingConfirmation(Instant.now(clock))));
    });
  }

  @Override
  public Mono<Order> confirm(OrderId orderId) {

    return orderRepository.findById(orderId)
        .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId.value()))).flatMap(order -> {

          if (order.status() == OrderStatus.SOLD) {
            return Mono.just(order);
          }

          if (order.status() == OrderStatus.EXPIRED) {
            return Mono.just(order);
          }

          /*
           * If the API is called before SQS has moved the
           * reservation to PENDING_CONFIRMATION, we perform
           * the transition first.
           */
          Mono<Void> prepareConfirmation;

          if (order.status() == OrderStatus.RESERVED) {

            prepareConfirmation = ticketRepository.moveToPendingConfirmation(order.eventId(),
                order.ticketIds(), order.id().value()).then();

          } else {

            prepareConfirmation = Mono.empty();
          }

          return prepareConfirmation.then(
                  ticketRepository.confirmSale(order.eventId(), order.ticketIds(), order.id().value()))
              .then(orderRepository.save(order.sold(Instant.now(clock))));
        });
  }
}