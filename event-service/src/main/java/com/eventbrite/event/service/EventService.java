package com.eventbrite.event.service;

import com.eventbrite.event.dto.CreateEventRequest;
import com.eventbrite.event.dto.EventResponse;
import com.eventbrite.event.dto.PageResponse;
import com.eventbrite.event.dto.UpdateEventRequest;
import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.Event;
import com.eventbrite.event.entity.EventStatus;
import com.eventbrite.event.exception.CapacityBelowBookingsException;
import com.eventbrite.event.exception.EventNotFoundException;
import com.eventbrite.event.exception.InvalidEventStateException;
import com.eventbrite.event.mapper.EventMapper;
import com.eventbrite.event.repository.EventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    // ---------- reads ----------

    /** Public catalogue: published events only, with filters + pagination. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchPublic(Category category, String city, String search,
                                                    Pageable pageable) {
        return search(category, city, search, EventStatus.PUBLISHED, pageable);
    }

    /** Admin view: every status, same filters. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchAdmin(Category category, String city, String search,
                                                   EventStatus status, Pageable pageable) {
        return search(category, city, search, status, pageable);
    }

    private PageResponse<EventResponse> search(Category category, String city, String search,
                                               EventStatus status, Pageable pageable) {
        Specification<Event> spec = byFilters(category, city, search, status);
        Page<EventResponse> page = repository.findAll(spec, pageable).map(EventMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public EventResponse getById(Long id) {
        return repository.findById(id)
                .map(EventMapper::toResponse)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    // ---------- admin writes ----------

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event saved = repository.save(EventMapper.toEntity(request));
        return EventMapper.toResponse(saved);
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest request) {
        Event event = getEntity(id);

        int seatsBooked = event.getCapacity() - event.getAvailableSeats();
        if (request.capacity() < seatsBooked) {
            throw new CapacityBelowBookingsException(id, request.capacity(), seatsBooked);
        }

        event.setName(request.name());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setVenue(request.venue());
        event.setCity(request.city());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setTicketPrice(request.ticketPrice());
        event.setStatus(request.status());
        if (request.capacity() != event.getCapacity()) {
            int delta = request.capacity() - event.getCapacity();
            event.setCapacity(request.capacity());
            event.setAvailableSeats(event.getAvailableSeats() + delta); // grow adds seats, shrink removes
        }

        // No save() call: inside @Transactional, dirty checking flushes the UPDATE on commit.
        return EventMapper.toResponse(event);
    }

    /** Only drafts and cancelled events can be hard-deleted; published ones must be cancelled first. */
    @Transactional
    public void delete(Long id) {
        Event event = getEntity(id);
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new InvalidEventStateException(id, event.getStatus(), "delete");
        }
        repository.delete(event);
    }

    // ---------- service-to-service endpoints (called by booking-service) ----------

    /**
     * The concurrency-critical operation of the whole system. Safety = @Transactional
     * around the read-check-write PLUS @Version on Event. See EventReserveConcurrencyTest.
     */
    @Transactional
    public EventResponse reserveSeats(Long id, int quantity) {
        Event event = getEntity(id);
        event.reserveSeats(quantity);
        return EventMapper.toResponse(event);
    }

    @Transactional
    public EventResponse releaseSeats(Long id, int quantity) {
        Event event = getEntity(id);
        event.releaseSeats(quantity);
        return EventMapper.toResponse(event);
    }

    // ---------- helpers ----------

    private Event getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    /**
     * Dynamic filter combination as a JPA Specification - one code path for any mix of
     * (category, city, text search, status), instead of one derived method per combination.
     */
    private static Specification<Event> byFilters(Category category, String city, String search,
                                                  EventStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase()));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
