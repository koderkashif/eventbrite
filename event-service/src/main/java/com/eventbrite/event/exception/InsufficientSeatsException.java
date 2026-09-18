package com.eventbrite.event.exception;

public class InsufficientSeatsException extends RuntimeException {

    public InsufficientSeatsException(Long eventId, int requested, int available) {
        super("Only " + available + " ticket(s) available for event " + eventId
                + " (requested " + requested + ")");
    }
}
