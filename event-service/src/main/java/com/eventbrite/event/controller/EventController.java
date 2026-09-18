package com.eventbrite.event.controller;

import com.eventbrite.event.dto.CreateEventRequest;
import com.eventbrite.event.dto.EventResponse;
import com.eventbrite.event.dto.PageResponse;
import com.eventbrite.event.dto.ReserveSeatsRequest;
import com.eventbrite.event.dto.UpdateEventRequest;
import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.EventStatus;
import com.eventbrite.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browsing is public; mutations require ADMIN (method security).
 * /reserve and /release are internal endpoints for booking-service (key-protected in the security chain).
 *
 * Pagination/sorting come straight from the query string:
 *   GET /api/events?page=0&size=20&sort=startTime,asc&category=TECH&city=Karachi&search=java
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EventResponse> listPublic(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return service.searchPublic(category, city, search, pageable);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<EventResponse> listAdmin(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.searchAdmin(category, city, search, status, pageable);
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEventRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ---- internal, called by booking-service with X-Internal-Api-Key ----

    @PostMapping("/{id}/reserve")
    public EventResponse reserve(@PathVariable Long id, @Valid @RequestBody ReserveSeatsRequest request) {
        return service.reserveSeats(id, request.quantity());
    }

    @PostMapping("/{id}/release")
    public EventResponse release(@PathVariable Long id, @Valid @RequestBody ReserveSeatsRequest request) {
        return service.releaseSeats(id, request.quantity());
    }
}
