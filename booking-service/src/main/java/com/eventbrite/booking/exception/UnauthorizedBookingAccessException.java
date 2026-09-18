package com.eventbrite.booking.exception;

public class UnauthorizedBookingAccessException extends RuntimeException {

    public UnauthorizedBookingAccessException(Long bookingId) {
        super("You do not have access to booking " + bookingId);
    }
}
