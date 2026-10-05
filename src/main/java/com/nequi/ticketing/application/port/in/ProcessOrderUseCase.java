package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.Order;
import reactor.core.publisher.Mono;

public interface ProcessOrderUseCase {
    Mono<Order> execute(Order order);
}
