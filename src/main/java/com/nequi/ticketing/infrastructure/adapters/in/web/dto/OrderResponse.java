package com.nequi.ticketing.infrastructure.adapters.in.web.dto;

import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.TicketId;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderId,
    String eventId,
    String userId,
    List<String> ticketIds,
    int quantity,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant reservationExpiresAt
) {

    public static OrderResponse from(Order order) {

        return new OrderResponse(
            order.id().value(),
            order.eventId().value(),
            order.userId(),
            order.ticketIds()
                .stream()
                .map(TicketId::value)
                .toList(),
            order.quantity(),
            order.status().name(),
            order.createdAt(),
            order.updatedAt(),
            order.reservationExpiresAt()
        );
    }
}