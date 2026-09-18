package com.eventbrite.booking.service;

import com.eventbrite.booking.client.EventServiceClient;
import com.eventbrite.booking.client.EventServiceClient.EventDetails;
import com.eventbrite.booking.dto.BookingCreateResult;
import com.eventbrite.booking.dto.BookingResponse;
import com.eventbrite.booking.dto.CreateBookingRequest;
import com.eventbrite.booking.entity.Booking;
import com.eventbrite.booking.entity.BookingStatus;
import com.eventbrite.booking.exception.BookingNotFoundException;
import com.eventbrite.booking.exception.IdempotencyKeyConflictException;
import com.eventbrite.booking.exception.InvalidBookingStateException;
import com.eventbrite.booking.exception.UnauthorizedBookingAccessException;
import com.eventbrite.booking.mapper.BookingMapper;
import com.eventbrite.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the booking flow ACROSS two services:
 *
 *   JWT caller -> idempotency check -> event-service /reserve (atomic, concurrency-safe)
 *              -> save booking (price snapshotted from event-service) -> confirm
 *
 * Two deliberate principles:
 *  1. Remote calls happen OUTSIDE local transactions - never hold a DB transaction
 *     open across an HTTP call (ties a pool connection to network latency).
 *  2. Compensation over distributed transactions - if the local save fails after a
 *     successful reserve, we release the seats back. No 2PC, no saga framework needed
 *     at this scale.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;

    public BookingService(BookingRepository bookingRepository, EventServiceClient eventServiceClient) {
        this.bookingRepository = bookingRepository;
        this.eventServiceClient = eventServiceClient;
    }

    public BookingCreateResult create(Long userId, String idempotencyKey, CreateBookingRequest request) {
        String key = normalize(idempotencyKey);

        // Fast path: same key seen before? Return the original booking, book nothing.
        if (key != null) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                Booking booking = existing.get();
                if (!booking.getUserId().equals(userId)) {
                    throw new IdempotencyKeyConflictException(key);
                }
                log.info("Idempotent replay for key {}: returning existing booking {}", key, booking.getId());
                return new BookingCreateResult(BookingMapper.toResponse(booking), true);
            }
        }

        // Remote, atomic: validates event exists + PUBLISHED + enough seats, then decrements.
        // Concurrency safety lives in event-service (@Transactional + @Version on Event).
        EventDetails event = eventServiceClient.reserveSeats(request.eventId(), request.quantity());

        try {
            Booking saved = bookingRepository.save(new Booking(
                    userId, request.eventId(), event.name(),
                    request.quantity(), event.ticketPrice(), key));
            log.info("Booking {} created: user {} booked {} ticket(s) on event {} (key={})",
                    saved.getBookingReference(), userId, request.quantity(), request.eventId(), key);
            return new BookingCreateResult(BookingMapper.toResponse(saved), false);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same key raced here; the DB unique constraint decided the winner.
            Booking winner = key != null
                    ? bookingRepository.findByIdempotencyKey(key).orElseThrow(() -> e)
                    : null;
            if (winner == null || !winner.getUserId().equals(userId)) {
                throw new IdempotencyKeyConflictException(key);
            }
            eventServiceClient.releaseSeats(request.eventId(), request.quantity()); // undo our reserve
            return new BookingCreateResult(BookingMapper.toResponse(winner), true);
        } catch (RuntimeException e) {
            eventServiceClient.releaseSeats(request.eventId(), request.quantity()); // compensation
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long requesterId, boolean admin, Long bookingId) {
        Booking booking = find(bookingId);
        checkOwnership(requesterId, admin, booking);
        return BookingMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    /**
     * Cancel = release the seats on event-service, then mark CANCELLED locally.
     * Remote-first: if the release fails, the booking stays CONFIRMED and the user
     * can retry - we never destroy the local record while the remote side still
     * holds the seats sold.
     */
    public void cancel(Long requesterId, boolean admin, Long bookingId) {
        Booking booking = find(bookingId);
        checkOwnership(requesterId, admin, booking);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus());
        }

        eventServiceClient.releaseSeats(booking.getEventId(), booking.getQuantity());
        booking.cancel();
        bookingRepository.save(booking);
        log.info("Booking {} cancelled: {} seat(s) returned to event {}",
                booking.getBookingReference(), booking.getQuantity(), booking.getEventId());
    }

    private void checkOwnership(Long requesterId, boolean admin, Booking booking) {
        if (!admin && !booking.getUserId().equals(requesterId)) {
            throw new UnauthorizedBookingAccessException(booking.getId());
        }
    }

    private Booking find(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    private static String normalize(String key) {
        return (key == null || key.isBlank()) ? null : key.trim();
    }
}
