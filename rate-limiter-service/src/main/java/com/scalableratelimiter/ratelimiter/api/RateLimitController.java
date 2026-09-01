package com.scalableratelimiter.ratelimiter.api;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimiterService;
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
    public RateLimitCheckResponse check(@RequestHeader(USER_ID_HEADER) String userId) {
        return new RateLimitCheckResponse(rateLimiterService.allowRequest(userId));
    }
}
