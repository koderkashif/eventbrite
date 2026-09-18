package com.eventbrite.event.dto;

import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.EventStatus;
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

public record UpdateEventRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        String description,

        @NotNull(message = "Category is required")
        Category category,

        @NotBlank(message = "Venue is required")
        String venue,

        @NotBlank(message = "City is required")
        String city,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        @Future(message = "End time must be in the future")
        LocalDateTime endTime,

        @NotNull(message = "Ticket price is required")
        @DecimalMin(value = "0.0", message = "Ticket price cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal ticketPrice,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        @NotNull(message = "Status is required")
        EventStatus status
) {
    @AssertTrue(message = "End time must be after start time")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
