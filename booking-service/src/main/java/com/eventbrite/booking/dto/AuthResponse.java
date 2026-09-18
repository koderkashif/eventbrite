package com.eventbrite.booking.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
