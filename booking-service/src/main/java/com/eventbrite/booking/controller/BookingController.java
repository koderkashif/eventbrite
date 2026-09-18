package com.eventbrite.booking.controller;

import com.eventbrite.booking.dto.BookingCreateResult;
import com.eventbrite.booking.dto.BookingResponse;
import com.eventbrite.booking.dto.CreateBookingRequest;
import com.eventbrite.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    /**
     * Retries are safe: send an Idempotency-Key header; a repeated request with the
     * same key returns the original booking (200) instead of double-booking (201).
     */
    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingCreateResult result = service.create(userId, idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.booking());
    }

    @GetMapping("/me")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal Long userId) {
        return service.myBookings(userId);
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@AuthenticationPrincipal Long userId,
                                   Authentication authentication,
                                   @PathVariable Long id) {
        return service.getById(userId, isAdmin(authentication), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Long userId,
                       Authentication authentication,
                       @PathVariable Long id) {
        service.cancel(userId, isAdmin(authentication), id);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
