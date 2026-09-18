package com.eventbrite.booking.exception;

import com.eventbrite.booking.entity.BookingStatus;

public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(Long bookingId, BookingStatus status) {
        super("Booking " + bookingId + " cannot be cancelled while its status is " + status);
    }
}
