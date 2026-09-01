package com.scalableratelimiter.app.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final String USER_ID_HEADER = "X-User-Id";

    private final RateLimitClient rateLimitClient;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitClient rateLimitClient, ObjectMapper objectMapper) {
        this.rateLimitClient = rateLimitClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/products/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required header: X-User-Id");
            return;
        }

        RateLimitDecision decision = rateLimitClient.checkRateLimit(userId);

        switch (decision) {
            case ALLOWED -> filterChain.doFilter(request, response);
            case RATE_LIMITED -> writeError(response, 429, "Rate limit exceeded");
            case UNAVAILABLE -> {
                log.warn("Rate limiting unavailable for user {} on {}; failing open",
                        userId, request.getRequestURI());
                filterChain.doFilter(request, response);
            }
        }
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", message));
    }
}
