package com.eventbrite.event.exception;

import com.eventbrite.event.entity.EventStatus;

public class InvalidEventStateException extends RuntimeException {

    public InvalidEventStateException(Long eventId, EventStatus status, String action) {
        super("Cannot " + action + " event " + eventId + " while its status is " + status);
    }
}
