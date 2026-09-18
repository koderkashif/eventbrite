package com.eventbrite.booking.exception;

/** Mapped from event-service's 409 INSUFFICIENT_SEATS (message passed through). */
public class InsufficientSeatsException extends RuntimeException {

    public InsufficientSeatsException(String message) {
        super(message);
    }
}
