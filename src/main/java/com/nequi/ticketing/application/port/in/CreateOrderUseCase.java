package com.nequi.ticketing.application.port.in;

import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.TicketId;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CreateOrderUseCase {

    Mono<Order> execute(
        String eventId,
        String userId,
        List<TicketId> ticketIds,
        String idempotencyKey
    );
}