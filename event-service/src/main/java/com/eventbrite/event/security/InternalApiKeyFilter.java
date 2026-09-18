package com.eventbrite.event.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protects the service-to-service endpoints (/api/events/{id}/reserve and /release)
 * with a shared internal API key. User traffic never carries this header, and the
 * booking-service never carries a user JWT on these calls - two separate trust paths.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final String internalApiKey;

    public InternalApiKeyFilter(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isInternalEndpoint = "POST".equals(request.getMethod())
                && (MATCHER.match("/api/events/*/reserve", path) || MATCHER.match("/api/events/*/release", path));

        if (isInternalEndpoint && !internalApiKey.equals(request.getHeader("X-Internal-Api-Key"))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid internal API key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
