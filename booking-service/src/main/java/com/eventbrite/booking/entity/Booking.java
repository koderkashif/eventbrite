package com.eventbrite.booking.entity;

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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_idempotency_key", columnNames = "idempotency_key"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 20)
    private String bookingReference;

    @Column(name = "user_id", nullable = false)
    private Long userId; // plain column: cross-service reference, no FK across service boundaries

    @Column(name = "event_id", nullable = false)
    private Long eventId; // lives in event-service's database - referenced by ID only

    /**
     * Snapshot from event-service at booking time. Events can be renamed later;
     * a booking receipt must keep showing what was booked (and at what price).
     * Deliberate denormalization across a service boundary.
     */
    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_per_ticket", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerTicket;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    /**
     * Idempotency: the client sends an Idempotency-Key header; a retried request with
     * the same key returns the original booking instead of booking twice. Enforced by
     * application code AND by the DB unique constraint (the actual last line of defense).
     */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Booking() {
        // JPA no-arg constructor
    }

    public Booking(Long userId, Long eventId, String eventName, int quantity,
                   BigDecimal pricePerTicket, String idempotencyKey) {
        this.userId = userId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.quantity = quantity;
        this.pricePerTicket = pricePerTicket;
        this.totalAmount = pricePerTicket.multiply(BigDecimal.valueOf(quantity)); // server computes the price
        this.status = BookingStatus.CONFIRMED;
        this.idempotencyKey = idempotencyKey;
        this.bookingReference = generateReference();
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

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    private static String generateReference() {
        return "BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public Long getId() {
        return id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPricePerTicket() {
        return pricePerTicket;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
