package com.eventbrite.booking.exception;

/** Mapped from event-service's 404 - the client shouldn't know two services exist. */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long eventId) {
        super("Event " + eventId + " does not exist");
    }
}
