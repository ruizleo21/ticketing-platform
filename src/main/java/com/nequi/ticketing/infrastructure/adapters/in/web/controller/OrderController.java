package com.nequi.ticketing.infrastructure.adapters.in.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nequi.ticketing.application.port.in.ConfirmOrderUseCase;
import com.nequi.ticketing.application.port.in.CreateOrderUseCase;
import com.nequi.ticketing.application.port.in.GetOrderStatusUseCase;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.infrastructure.adapters.in.web.dto.CreateOrderRequest;
import com.nequi.ticketing.infrastructure.adapters.in.web.dto.OrderResponse;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final CreateOrderUseCase createOrderUseCase;
  private final GetOrderStatusUseCase getOrderStatusUseCase;
  private final ConfirmOrderUseCase confirmOrderUseCase;

  public OrderController(
      CreateOrderUseCase createOrderUseCase,
      GetOrderStatusUseCase getOrderStatusUseCase,
      ConfirmOrderUseCase confirmOrderUseCase
  ) {
    this.createOrderUseCase = createOrderUseCase;
    this.getOrderStatusUseCase = getOrderStatusUseCase;
    this.confirmOrderUseCase = confirmOrderUseCase;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Mono<OrderResponse> create(
      @Valid @RequestBody CreateOrderRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey
  ) {
    return createOrderUseCase
        .execute(
            request.eventId(),
            request.userId(),
            request.ticketIds()
                .stream()
                .map(TicketId::new)
                .toList(),
            idempotencyKey
        )
        .map(OrderResponse::from);
  }

  @GetMapping("/{orderId}")
  public Mono<OrderResponse> get(
      @PathVariable String orderId
  ) {

    return getOrderStatusUseCase
        .execute(
            new OrderId(orderId)
        )
        .map(OrderResponse::from);
  }

  @PostMapping("/{orderId}/confirm")
  public Mono<OrderResponse> confirm(
      @PathVariable String orderId
  ) {

    return confirmOrderUseCase
        .confirm(
            new OrderId(orderId)
        )
        .map(OrderResponse::from);
  }
}