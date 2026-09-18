package com.eventbrite.event.exception;

public class CapacityBelowBookingsException extends RuntimeException {

    public CapacityBelowBookingsException(Long eventId, int requestedCapacity, int seatsBooked) {
        super("Capacity " + requestedCapacity + " is below the " + seatsBooked
                + " seat(s) already booked for event " + eventId);
    }
}
