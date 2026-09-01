package com.scalableratelimiter.ratelimiter.api;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimiterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rate-limit")
public class RateLimitController {

    static final String USER_ID_HEADER = "X-User-Id";

    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitCheckResponse> check(@RequestHeader(USER_ID_HEADER) String userId) {
        RateLimitDecision decision = rateLimiterService.checkRequest(userId);
        HttpStatus status = decision == RateLimitDecision.UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(new RateLimitCheckResponse(decision));
    }
}
