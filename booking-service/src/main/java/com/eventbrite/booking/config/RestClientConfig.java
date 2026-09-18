package com.eventbrite.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for event-service with explicit timeouts. Without these, a hung
 * event-service would tie up booking-service threads until the OS-level timeout
 * (minutes) - one down service takes down the other. 2s connect / 5s read instead.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient eventServiceRestClient(RestClient.Builder builder,
                                             @Value("${app.event-service.base-url}") String baseUrl,
                                             @Value("${app.event-service.connect-timeout-ms}") int connectTimeoutMs,
                                             @Value("${app.event-service.read-timeout-ms}") int readTimeoutMs,
                                             @Value("${app.internal-api-key}") String internalApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey) // trust path for /reserve, /release
                .build();
    }
}
