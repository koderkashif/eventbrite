package com.eventbrite.booking.mapper;

import com.eventbrite.booking.dto.BookingResponse;
import com.eventbrite.booking.entity.Booking;

public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getBookingReference(), b.getEventId(),
                b.getEventName(), b.getQuantity(), b.getPricePerTicket(), b.getTotalAmount(),
                b.getStatus().name(), b.getIdempotencyKey(), b.getCreatedAt());
    }
}
