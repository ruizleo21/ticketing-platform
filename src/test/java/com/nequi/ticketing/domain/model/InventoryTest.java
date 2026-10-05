package com.nequi.ticketing.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void shouldAcceptValidInventoryInvariant() {
        Inventory inventory = new Inventory(new EventId("e"), 100, 90, 5, 3, 2, 1);

        assertAll(
            () -> assertEquals(new EventId("e"), inventory.eventId()),
            () -> assertEquals(100, inventory.totalTickets()),
            () -> assertEquals(90, inventory.availableTickets()),
            () -> assertEquals(5, inventory.reservedTickets()),
            () -> assertEquals(3, inventory.soldTickets()),
            () -> assertEquals(2, inventory.complimentaryTickets()),
            () -> assertEquals(1, inventory.version())
        );
    }

    @Test
    void shouldRejectBrokenInventoryInvariant() {
        assertThrows(IllegalArgumentException.class, () ->
            new Inventory(new EventId("e"), 100, 90, 5, 3, 3, 1));
    }

    @Test
    void shouldRejectNegativeTotalTickets() {
        assertThrows(IllegalArgumentException.class, () ->
            new Inventory(new EventId("e"), -1, 0, 0, 0, 0, 1));
    }

    @Test
    void shouldRejectNegativeAvailableTickets() {
        assertThrows(IllegalArgumentException.class, () ->
            new Inventory(new EventId("e"), 1, -1, 1, 0, 1, 1));
    }

    @Test
    void shouldRejectNegativeReservedTickets() {
        assertThrows(IllegalArgumentException.class, () ->
            new Inventory(new EventId("e"), 1, 1, -1, 0, 1, 1));
    }

    @Test
    void shouldRejectNegativeSoldTickets() {
        assertThrows(IllegalArgumentException.class, () ->
            new Inventory(new EventId("e"), 1, 1, 0, -1, 1, 1));
    }

    @Test
    void shouldRejectNegativeComplimentaryTickets() {
        assertThrows(IllegalArgumentException.class, () ->
            new Inventory(new EventId("e"), 1, 1, 0, 0, -1, 1));
    }

    @Test
    void shouldImplementRecordEqualityAndStringRepresentation() {
        Inventory inventory = new Inventory(new EventId("e"), 10, 6, 2, 1, 1, 4);
        Inventory sameInventory = new Inventory(new EventId("e"), 10, 6, 2, 1, 1, 4);
        Inventory differentInventory = new Inventory(new EventId("e"), 10, 5, 3, 1, 1, 4);

        assertEquals(inventory, sameInventory);
        assertEquals(inventory.hashCode(), sameInventory.hashCode());
        assertNotEquals(inventory, differentInventory);
        assertTrue(inventory.toString().contains("totalTickets=10"));
    }
}
