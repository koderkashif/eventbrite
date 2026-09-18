package com.eventbrite.event.mapper;

import com.eventbrite.event.dto.CreateEventRequest;
import com.eventbrite.event.dto.EventResponse;
import com.eventbrite.event.entity.Event;

/** Entity <-> DTO translation in one place; entities never leak to the API. */
public final class EventMapper {

    private EventMapper() {
    }

    public static Event toEntity(CreateEventRequest r) {
        return new Event(r.name(), r.description(), r.category(), r.venue(), r.city(),
                r.startTime(), r.endTime(), r.ticketPrice(), r.capacity());
    }

    public static EventResponse toResponse(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getDescription(), e.getCategory(),
                e.getVenue(), e.getCity(), e.getStartTime(), e.getEndTime(), e.getTicketPrice(),
                e.getCapacity(), e.getAvailableSeats(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
