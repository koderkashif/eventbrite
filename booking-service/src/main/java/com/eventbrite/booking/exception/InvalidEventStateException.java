package com.eventbrite.booking.exception;

/** Mapped from event-service's 409 INVALID_EVENT_STATE (e.g. event not PUBLISHED). */
public class InvalidEventStateException extends RuntimeException {

    public InvalidEventStateException(String message) {
        super(message);
    }
}
