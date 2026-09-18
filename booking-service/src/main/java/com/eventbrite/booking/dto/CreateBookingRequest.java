package com.eventbrite.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The client never sends a price - the backend computes it from event-service data. */
public record CreateBookingRequest(

        @NotNull(message = "Event is required")
        Long eventId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Must book at least 1 ticket")
        @Max(value = 10, message = "Maximum 10 tickets per booking")
        Integer quantity
) {
}
