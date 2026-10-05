package com.nequi.ticketing.domain.model;

public record Inventory(
        EventId eventId,
        int totalTickets,
        int availableTickets,
        int reservedTickets,
        int soldTickets,
        int complimentaryTickets,
        long version
) {
    public Inventory {
        if (totalTickets < 0 || availableTickets < 0 || reservedTickets < 0
                || soldTickets < 0 || complimentaryTickets < 0) {
            throw new IllegalArgumentException("Inventory counters cannot be negative");
        }
        if (availableTickets + reservedTickets + soldTickets + complimentaryTickets != totalTickets) {
            throw new IllegalArgumentException("Inventory invariant violated");
        }
    }
}
