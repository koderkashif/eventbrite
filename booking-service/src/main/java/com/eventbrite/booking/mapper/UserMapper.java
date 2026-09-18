package com.eventbrite.booking.mapper;

import com.eventbrite.booking.dto.UserResponse;
import com.eventbrite.booking.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name());
    }
}
