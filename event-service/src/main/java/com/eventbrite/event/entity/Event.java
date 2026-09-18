package com.eventbrite.event.entity;

import com.eventbrite.event.exception.InsufficientSeatsException;
import com.eventbrite.event.exception.InvalidEventStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "ticket_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Optimistic locking: every UPDATE carries "WHERE version = ?" - if another
    // transaction changed this row first, ours matches 0 rows, Spring throws
    // OptimisticLockingFailureException and the whole transaction rolls back.
    // This is what makes reserveSeats() safe when many bookings race for the last seats.
    @Version
    @Column(nullable = false)
    private Long version;

    protected Event() {
        // JPA no-arg constructor; protected so app code can't build an invalid empty Event.
    }

    public Event(String name, String description, Category category, String venue, String city,
                 LocalDateTime startTime, LocalDateTime endTime, BigDecimal ticketPrice, int capacity) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.venue = venue;
        this.city = city;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ticketPrice = ticketPrice;
        this.capacity = capacity;
        this.availableSeats = capacity; // new events start fully bookable
        this.status = EventStatus.DRAFT; // admin creates a draft, then publishes via update
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Business rules live with the data they protect. Both checks run INSIDE the caller's
     * transaction; combined with @Version, 20 racing reservations for 1 seat produce exactly
     * 1 success and 19 rolled-back transactions (see EventReserveConcurrencyTest).
     */
    public void reserveSeats(int quantity) {
        if (status != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException(id, status, "reserve seats on");
        }
        if (quantity > availableSeats) {
            throw new InsufficientSeatsException(id, quantity, availableSeats);
        }
        availableSeats -= quantity;
    }

    /** Compensating action for a cancelled booking. Capped at capacity to stay sane. */
    public void releaseSeats(int quantity) {
        availableSeats = Math.min(capacity, availableSeats + quantity);
    }

    public void cancel() {
        this.status = EventStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(BigDecimal ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
