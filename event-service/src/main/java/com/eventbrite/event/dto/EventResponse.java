package com.eventbrite.event.dto;

import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        String description,
        Category category,
        String venue,
        String city,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal ticketPrice,
        int capacity,
        int availableSeats,
        EventStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
