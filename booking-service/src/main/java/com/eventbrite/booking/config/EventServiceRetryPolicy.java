package com.eventbrite.booking.config;

import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;

/** Shared retry predicate - kept separate so the policy is testable and citable. */
final class EventServiceRetryPolicy {

    private EventServiceRetryPolicy() {
    }

    /**
     * True only for failures that provably happened BEFORE the request reached the
     * server: connection refused means no TCP connection was ever established.
     * Everything else (read timeouts, 5xx, garbage responses) may have side effects
     * server-side and must not be blindly retried.
     */
    static boolean isDefinitelyNotDelivered(Throwable t) {
        return t instanceof ResourceAccessException && t.getCause() instanceof ConnectException;
    }
}
