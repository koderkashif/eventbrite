package com.eventbrite.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        String bookingReference,
        Long eventId,
        String eventName,
        int quantity,
        BigDecimal pricePerTicket,
        BigDecimal totalAmount,
        String status,
        String idempotencyKey,
        LocalDateTime createdAt
) {
}
