package com.nequi.ticketing.application.port.out;

import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Inventory;
import reactor.core.publisher.Mono;

public interface InventoryRepository {
    Mono<Inventory> create(Inventory inventory);
    Mono<Inventory> findByEventId(EventId eventId);

    /**
     * Must be implemented using an atomic conditional update.
     */
    Mono<Inventory> reserve(EventId eventId, int quantity);

    /**
     * Must be implemented using an atomic conditional update.
     */
    Mono<Inventory> releaseReservation(EventId eventId, int quantity);

    /**
     * Must be implemented using an atomic conditional update.
     */
    Mono<Inventory> confirmSale(EventId eventId, int quantity);
}
