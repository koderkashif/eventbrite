package com.eventbrite.event.dto;

import com.eventbrite.event.entity.Category;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateEventRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        String description,

        @NotNull(message = "Category is required")
        Category category,

        @NotBlank(message = "Venue is required")
        @Size(max = 200, message = "Venue must be at most 200 characters")
        String venue,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        @Future(message = "End time must be in the future")
        LocalDateTime endTime,

        @NotNull(message = "Ticket price is required")
        @DecimalMin(value = "0.0", message = "Ticket price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Ticket price must have at most 2 decimal places")
        BigDecimal ticketPrice,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 100000, message = "Capacity must be at most 100000")
        Integer capacity
) {
    // Cross-field rules that single-field annotations can't express.
    // Shows up in validation errors under the property "validTimeRange".

    @AssertTrue(message = "End time must be after start time")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "Available seats cannot be greater than capacity")
    public boolean isSeatsWithinCapacity() {
        return true; // placeholder symmetric with update DTO; create always starts seats = capacity
    }
}
