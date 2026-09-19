package com.eventbrite.booking.client;

import com.eventbrite.booking.exception.EventNotFoundException;
import com.eventbrite.booking.exception.EventServiceUnavailableException;
import com.eventbrite.booking.exception.InsufficientSeatsException;
import com.eventbrite.booking.exception.InvalidEventStateException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The single place booking-service talks to event-service over HTTP.
 *
 * Every call is wrapped:  retry(only-if-never-delivered) INSIDE circuit breaker.
 * Composition order matters - retry inside the breaker means one logical call
 * (with up to 3 attempts) counts as ONE circuit-breaker sample, not three.
 *
 * Error translation contract:
 *   - 404 / 409 from event-service  -> business exceptions (NOT infra failures; don't trip the breaker)
 *   - connect refused / timeout     -> EventServiceUnavailableException (503; DOES trip the breaker after retries)
 *   - circuit open                  -> EventServiceUnavailableException, answered in ~0ms (fail fast)
 */
@Component
public class EventServiceClient {

    private static final Logger log = LoggerFactory.getLogger(EventServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final Retry connectRetry;

    /** The fields booking-service needs from an event (subset of event-service's EventResponse). */
    public record EventDetails(Long id, String name, BigDecimal ticketPrice, String status, int availableSeats) {
    }

    public EventServiceClient(@Qualifier("eventServiceRestClient") RestClient restClient,
                              ObjectMapper objectMapper,
                              @Qualifier("eventServiceCircuitBreaker") CircuitBreaker circuitBreaker,
                              @Qualifier("eventServiceConnectRetry") Retry connectRetry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
        this.connectRetry = connectRetry;
    }

    public EventDetails reserveSeats(Long eventId, int quantity) {
        long start = System.nanoTime();
        try {
            EventDetails details = protectedCall(() -> doReserve(eventId, quantity)).get();
            log.info("reserve event {} -> {} seat(s) left, took {} ms",
                    eventId, details.availableSeats(), elapsedMs(start));
            return details;
        } catch (CallNotPermittedException e) {
            log.warn("circuit breaker OPEN - failing fast for reserve event {}", eventId);
            throw new EventServiceUnavailableException(eventId, "circuit breaker open");
        } catch (RestClientException e) {
            log.error("event-service unreachable (reserve event {}): {}", eventId, e.getMessage());
            throw new EventServiceUnavailableException(eventId, e);
        }
    }

    public void releaseSeats(Long eventId, int quantity) {
        long start = System.nanoTime();
        try {
            protectedCall(() -> {
                doRelease(eventId, quantity);
                return null;
            }).get();
            log.info("release {} seat(s) for event {} ok, took {} ms", quantity, eventId, elapsedMs(start));
        } catch (CallNotPermittedException e) {
            log.warn("circuit breaker OPEN - failing fast for release event {}", eventId);
            throw new EventServiceUnavailableException(eventId, "circuit breaker open");
        } catch (RestClientException e) {
            log.error("event-service unreachable (release event {}): {}", eventId, e.getMessage());
            throw new EventServiceUnavailableException(eventId, e);
        }
    }

    private <T> Supplier<T> protectedCall(Supplier<T> call) {
        // static decorator form: retry(inner) wrapped by circuitBreaker(outer)
        return CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(connectRetry, call));
    }

    // ---- raw HTTP (no resilience logic here - the wrappers above own it) ----

    private EventDetails doReserve(Long eventId, int quantity) {
        return restClient.post()
                .uri("/api/events/{id}/reserve", eventId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new EventNotFoundException(eventId);
                })
                .onStatus(status -> status.value() == 409, (req, res) -> {
                    DownstreamError error = readError(res);
                    if ("INSUFFICIENT_SEATS".equals(error.code())) {
                        throw new InsufficientSeatsException(error.message());
                    }
                    throw new InvalidEventStateException(error.message()); // e.g. not PUBLISHED
                })
                .body(EventDetails.class);
    }

    private void doRelease(Long eventId, int quantity) {
        restClient.post()
                .uri("/api/events/{id}/release", eventId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .toBodilessEntity();
    }

    private record DownstreamError(String code, String message) {
    }

    private DownstreamError readError(ClientHttpResponse response) {
        try (InputStream in = response.getBody()) {
            return objectMapper.readValue(in, DownstreamError.class);
        } catch (IOException e) {
            return new DownstreamError("UNKNOWN", "event-service rejected the request");
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Used by the bulk seeder to page published events through the service boundary. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BulkEventsPage(java.util.List<BulkEventRef> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BulkEventRef(Long id, String name, BigDecimal ticketPrice) {
    }
}
