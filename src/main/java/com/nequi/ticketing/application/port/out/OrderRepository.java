package com.nequi.ticketing.application.port.out;

import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepository {
    Mono<Order> save(Order order);
    Mono<Order> findById(OrderId orderId);
    Mono<Order> findByIdempotencyKey(String key);
    Flux<Order> findExpiredReservations(java.time.Instant now);
}
