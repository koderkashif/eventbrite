package com.eventbrite.booking.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Booking " + id + " does not exist");
    }
}
