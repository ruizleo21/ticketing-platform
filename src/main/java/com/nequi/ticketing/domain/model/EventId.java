package com.nequi.ticketing.domain.model;

import java.util.UUID;

public record EventId(String value) {
    public static EventId newId() {
        return new EventId(UUID.randomUUID().toString());
    }
}
