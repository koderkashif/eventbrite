package com.eventbrite.booking.dto;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role
) {
}
