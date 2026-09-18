package com.eventbrite.booking.client;

import com.eventbrite.booking.exception.EventNotFoundException;
import com.eventbrite.booking.exception.EventServiceUnavailableException;
import com.eventbrite.booking.exception.InsufficientSeatsException;
import com.eventbrite.booking.exception.InvalidEventStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The single place booking-service talks to event-service over HTTP.
 * Downstream errors are translated into this service's own exceptions, so the
 * rest of the code never sees HTTP details - and callers get clean 404/409/503s.
 */
@Component
public class EventServiceClient {

    private static final Logger log = LoggerFactory.getLogger(EventServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** The fields booking-service needs from an event (subset of event-service's EventResponse). */
    public record EventDetails(Long id, String name, BigDecimal ticketPrice, String status, int availableSeats) {
    }

    public EventServiceClient(@Qualifier("eventServiceRestClient") RestClient restClient,
                              ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public EventDetails reserveSeats(Long eventId, int quantity) {
        try {
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
        } catch (RestClientException e) {
            // connection refused / timeout - event-service is unreachable
            log.error("event-service call failed (reserve event {}): {}", eventId, e.getMessage());
            throw new EventServiceUnavailableException(eventId, e);
        }
    }

    public void releaseSeats(Long eventId, int quantity) {
        try {
            restClient.post()
                    .uri("/api/events/{id}/release", eventId)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("event-service call failed (release event {}): {}", eventId, e.getMessage());
            throw new EventServiceUnavailableException(eventId, e);
        }
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
}
