package com.eventbrite.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Internal request body for POST /api/events/{id}/reserve and /release.
 * Only booking-service calls these (X-Internal-Api-Key required).
 */
public record ReserveSeatsRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 20, message = "Quantity must be at most 20")
        Integer quantity
) {
}
