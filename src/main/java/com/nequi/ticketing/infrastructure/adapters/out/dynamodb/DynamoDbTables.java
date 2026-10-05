package com.nequi.ticketing.infrastructure.adapters.out.dynamodb;

public final class DynamoDbTables {

    private DynamoDbTables() {
    }

    public static final String EVENTS =
        "ticketing-events";

    public static final String INVENTORY =
        "ticketing-inventory";

    public static final String TICKETS =
        "ticketing-tickets";

    public static final String ORDERS =
        "ticketing-orders";
}