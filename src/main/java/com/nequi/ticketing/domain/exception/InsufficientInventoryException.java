package com.nequi.ticketing.domain.exception;

public class InsufficientInventoryException extends DomainException {
    public InsufficientInventoryException() {
        super("There is not enough inventory available");
    }
}
