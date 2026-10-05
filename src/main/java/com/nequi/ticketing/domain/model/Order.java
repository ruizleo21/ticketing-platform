package com.nequi.ticketing.domain.model;

import java.time.Instant;
import java.util.List;

public record Order(
    OrderId id,
    EventId eventId,
    String userId,
    List<TicketId> ticketIds,
    OrderStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant reservationExpiresAt,
    String idempotencyKey
) {

    public Order {
        if (ticketIds == null || ticketIds.isEmpty() || ticketIds.size() > 10) {
            throw new IllegalArgumentException(
                "Ticket count must be between 1 and 10"
            );
        }

        if (ticketIds.stream().distinct().count() != ticketIds.size()) {
            throw new IllegalArgumentException(
                "Ticket ids must be unique"
            );
        }

        ticketIds = List.copyOf(ticketIds);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                "Idempotency key is required"
            );
        }
    }

    public int quantity() {
        return ticketIds.size();
    }

    public Order pendingConfirmation(Instant now) {
        return new Order(
            id,
            eventId,
            userId,
            ticketIds,
            OrderStatus.PENDING_CONFIRMATION,
            createdAt,
            now,
            reservationExpiresAt,
            idempotencyKey
        );
    }

    public Order sold(Instant now) {
        return new Order(
            id,
            eventId,
            userId,
            ticketIds,
            OrderStatus.SOLD,
            createdAt,
            now,
            null,
            idempotencyKey
        );
    }

    public Order expired(Instant now) {
        return new Order(
            id,
            eventId,
            userId,
            ticketIds,
            OrderStatus.EXPIRED,
            createdAt,
            now,
            reservationExpiresAt,
            idempotencyKey
        );
    }
}