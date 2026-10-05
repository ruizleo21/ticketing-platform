package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import reactor.core.publisher.Mono;

public interface ConfirmOrderUseCase {
    Mono<Order> confirm(OrderId orderId);
}
