package com.eventbrite.event.security;

import com.eventbrite.event.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Stateless API security:
 *   - GET /api/events/**            -> public browsing
 *   - POST/PUT/DELETE /api/events/** -> ADMIN (enforced by @PreAuthorize in the controller)
 *   - /reserve, /release            -> internal API key (InternalApiKeyFilter)
 *   - everything else with a role    -> needs a valid JWT
 * 401/403 responses use the same JSON error shape as GlobalExceptionHandler.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper; // Spring's configured mapper (handles LocalDateTime)
    private final String internalApiKey;

    public SecurityConfig(JwtService jwtService, ObjectMapper objectMapper,
                          @Value("${app.internal-api-key}") String internalApiKey) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.internalApiKey = internalApiKey;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // no cookies -> no CSRF surface
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/events/*/reserve", "/api/events/*/release").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(this::unauthorized)
                    .accessDeniedHandler(this::forbidden))
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new InternalApiKeyFilter(internalApiKey), JwtAuthFilter.class);
        return http.build();
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response,
                              org.springframework.security.core.AuthenticationException ex) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required. Send a valid Bearer token.");
    }

    private void forbidden(HttpServletRequest request, HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException ex) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ApiError(LocalDateTime.now(), status, code, message, null));
    }
}
