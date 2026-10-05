package com.nequi.ticketing.application.port.out;

import com.nequi.ticketing.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderPublisher {
    Mono<Void> publish(Order order);
}
