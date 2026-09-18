package com.eventbrite.event.service;

import com.eventbrite.event.dto.CreateEventRequest;
import com.eventbrite.event.dto.UpdateEventRequest;
import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.Event;
import com.eventbrite.event.entity.EventStatus;
import com.eventbrite.event.exception.CapacityBelowBookingsException;
import com.eventbrite.event.exception.InvalidEventStateException;
import com.eventbrite.event.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Unit tests for the business rules - repository mocked, no Spring context. */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository repository;

    @InjectMocks
    private EventService service;

    private Event publishedEvent(int capacity, int availableSeats) {
        Event e = new Event("Test Event", "desc", Category.TECH, "Venue", "Bengaluru",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3),
                new BigDecimal("50.00"), capacity);
        e.setStatus(EventStatus.PUBLISHED);
        e.setAvailableSeats(availableSeats);
        return e;
    }

    @Test
    void createMapsRequestAndStartsAsDraftWithSeatsEqualToCapacity() {
        when(repository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new CreateEventRequest("New", "d", Category.MUSIC, "V", "Lahore",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2),
                new BigDecimal("10.00"), 75);

        var response = service.create(request);

        assertEquals(EventStatus.DRAFT, response.status());
        assertEquals(75, response.capacity());
        assertEquals(75, response.availableSeats());
    }

    @Test
    void updateRejectsCapacityBelowAlreadyBookedSeats() {
        // capacity 100, 30 sold -> available 70
        Event event = publishedEvent(100, 70);
        when(repository.findById(1L)).thenReturn(Optional.of(event));

        var request = new UpdateEventRequest("New name", "d", Category.TECH, "V", "Bengaluru",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2),
                new BigDecimal("50.00"), 20, EventStatus.PUBLISHED); // 20 < 30 booked

        assertThrows(CapacityBelowBookingsException.class, () -> service.update(1L, request));
    }

    @Test
    void updateGrowingCapacityAddsSeatsToAvailable() {
        Event event = publishedEvent(100, 70); // 30 booked
        when(repository.findById(1L)).thenReturn(Optional.of(event));

        var request = new UpdateEventRequest("New name", "d", Category.TECH, "V", "Bengaluru",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2),
                new BigDecimal("50.00"), 150, EventStatus.PUBLISHED);

        var response = service.update(1L, request);

        assertEquals(150, response.capacity());
        assertEquals(120, response.availableSeats()); // 70 + 50 new seats, 30 still booked
    }

    @Test
    void publishedEventCannotBeDeleted() {
        Event event = publishedEvent(10, 10);
        when(repository.findById(2L)).thenReturn(Optional.of(event));

        assertThrows(InvalidEventStateException.class, () -> service.delete(2L));
    }
}
