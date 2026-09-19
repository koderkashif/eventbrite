package com.eventbrite.booking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience around event-service calls. Programmatic (not annotation/yml) on purpose:
 * the composition order matters and is worth seeing explicitly.
 *
 * Circuit breaker (eventService):
 *   CLOSED -> measure failure rate over the last 10 calls (min 5 before it counts)
 *   -> >= 50% failures -> OPEN: every call fails fast (CallNotPermittedException),
 *      no thread is held hostage by a 2s connect + 5s read timeout
 *   -> after 10s -> HALF_OPEN: 3 probe calls through; successes close the circuit.
 *
 * Retry (eventServiceConnect) - deliberately narrow:
 *   ONLY "connection refused" is retried. If the TCP connect never succeeded, the
 *   request provably never reached event-service, so retrying even a state-changing
 *   POST /reserve cannot double-book. Read timeouts are NOT retried here: the request
 *   may have succeeded server-side with a lost response - that class of retry is the
 *   client's job and is covered by the Idempotency-Key instead.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker eventServiceCircuitBreaker() {
        return CircuitBreaker.of("eventService", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)                                  // % failures to trip
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Business answers are SUCCESSFUL infrastructure calls: a 404 or a
                // 409 "sold out" means event-service is healthy - those must never
                // nudge the failure rate. Only transport-level trouble trips the circuit.
                .ignoreExceptions(
                        com.eventbrite.booking.exception.EventNotFoundException.class,
                        com.eventbrite.booking.exception.InsufficientSeatsException.class,
                        com.eventbrite.booking.exception.InvalidEventStateException.class)
                .build());
    }

    @Bean
    public Retry eventServiceConnectRetry() {
        return Retry.of("eventServiceConnect", RetryConfig.custom()
                .maxAttempts(3)                                             // 1 try + 2 retries
                .waitDuration(Duration.ofMillis(150))
                .retryOnException(EventServiceRetryPolicy::isDefinitelyNotDelivered)
                .build());
    }
}
