package com.eventbrite.booking.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String key) {
        super("Idempotency key " + key + " was already used by a different user");
    }
}
