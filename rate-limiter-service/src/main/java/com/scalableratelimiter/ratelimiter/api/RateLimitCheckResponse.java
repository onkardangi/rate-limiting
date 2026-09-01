package com.scalableratelimiter.ratelimiter.api;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitDecision;

public record RateLimitCheckResponse(RateLimitDecision decision) {
}
