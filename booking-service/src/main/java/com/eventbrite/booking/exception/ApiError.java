package com.eventbrite.booking.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Every error from this service has the same JSON shape:
 * { "timestamp": "...", "status": 409, "code": "INSUFFICIENT_SEATS", "message": "...", "fieldErrors": {...} }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public static ApiError of(HttpStatus status, String code, String message) {
        return new ApiError(LocalDateTime.now(), status.value(), code, message, null);
    }

    public static ApiError withFieldErrors(HttpStatus status, String code, String message,
                                           Map<String, String> fieldErrors) {
        return new ApiError(LocalDateTime.now(), status.value(), code, message, fieldErrors);
    }
}
