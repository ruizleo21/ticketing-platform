package com.nequi.ticketing.domain.model;

import java.time.Instant;

public record Event(
        EventId id,
        String name,
        Instant date,
        String venue,
        int totalCapacity
) {
    public Event {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Event name is required");
        if (date == null) throw new IllegalArgumentException("Event date is required");
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("Venue is required");
        if (totalCapacity <= 0) throw new IllegalArgumentException("Capacity must be greater than zero");
    }
}
