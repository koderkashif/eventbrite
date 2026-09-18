package com.eventbrite.booking.exception;

/** event-service unreachable / timed out - surfaced as 503, never as a stack trace. */
public class EventServiceUnavailableException extends RuntimeException {

    public EventServiceUnavailableException(Long eventId, Throwable cause) {
        super("Event service is currently unavailable. Please try again shortly. (event " + eventId + ")");
        initCause(cause);
    }
}
