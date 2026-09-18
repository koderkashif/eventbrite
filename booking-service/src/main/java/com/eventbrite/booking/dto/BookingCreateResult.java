package com.eventbrite.booking.dto;

/**
 * Distinguishes a fresh booking (HTTP 201) from an idempotent replay (HTTP 200,
 * existing booking returned untouched).
 */
public record BookingCreateResult(
        BookingResponse booking,
        boolean replayed
) {
}
