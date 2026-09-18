package com.eventbrite.event.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long id) {
        super("Event " + id + " does not exist");
    }
}
