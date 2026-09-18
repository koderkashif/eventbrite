package com.eventbrite.booking.service;

import com.eventbrite.booking.client.EventServiceClient;
import com.eventbrite.booking.client.EventServiceClient.EventDetails;
import com.eventbrite.booking.dto.BookingCreateResult;
import com.eventbrite.booking.dto.CreateBookingRequest;
import com.eventbrite.booking.entity.Booking;
import com.eventbrite.booking.entity.BookingStatus;
import com.eventbrite.booking.exception.UnauthorizedBookingAccessException;
import com.eventbrite.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventServiceClient eventServiceClient;

    @InjectMocks
    private BookingService service;

    private static final EventDetails EVENT =
            new EventDetails(5L, "Microservices Workshop", new BigDecimal("75.00"), "PUBLISHED", 38);

    @Test
    void createComputesTotalOnServerAndConfirms() {
        when(eventServiceClient.reserveSeats(5L, 2)).thenReturn(EVENT);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingCreateResult result = service.create(9L, null, new CreateBookingRequest(5L, 2));

        assertNotNull(result.booking().bookingReference());
        assertEquals("CONFIRMED", result.booking().status());
        assertEquals(new BigDecimal("75.00"), result.booking().pricePerTicket());
        assertEquals(new BigDecimal("150.00"), result.booking().totalAmount()); // price * qty, computed server-side
        assertEquals(false, result.replayed());
        verify(eventServiceClient, never()).releaseSeats(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void sameIdempotencyKeyReturnsExistingBookingWithoutBookingAgain() {
        Booking existing = new Booking(9L, 5L, "Microservices Workshop", 2,
                new BigDecimal("75.00"), "key-abc");
        when(bookingRepository.findByIdempotencyKey("key-abc")).thenReturn(Optional.of(existing));

        BookingCreateResult result = service.create(9L, "key-abc", new CreateBookingRequest(5L, 2));

        assertEquals(true, result.replayed());
        assertEquals(existing.getBookingReference(), result.booking().bookingReference());
        verifyNoInteractions(eventServiceClient); // no second reserve
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void failedSaveReleasesSeatsBackCompensation() {
        when(eventServiceClient.reserveSeats(5L, 2)).thenReturn(EVENT);
        when(bookingRepository.save(any(Booking.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class,
                () -> service.create(9L, null, new CreateBookingRequest(5L, 2)));

        verify(eventServiceClient).releaseSeats(5L, 2); // seats given back - no leakage
    }

    @Test
    void userCannotCancelSomeoneElsesBooking() {
        Booking booking = new Booking(2L, 5L, "Microservices Workshop", 1,
                new BigDecimal("75.00"), null);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(withId(booking, 1L)));

        assertThrows(UnauthorizedBookingAccessException.class, () -> service.cancel(9L, false, 1L));
        verifyNoInteractions(eventServiceClient);
    }

    @Test
    void cancelReleasesSeatsAndMarksCancelled() {
        Booking booking = new Booking(9L, 5L, "Microservices Workshop", 2,
                new BigDecimal("75.00"), null);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(withId(booking, 1L)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(9L, false, 1L);

        verify(eventServiceClient).releaseSeats(5L, 2);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    private static Booking withId(Booking booking, Long id) {
        try {
            var field = Booking.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(booking, id);
            return booking;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
