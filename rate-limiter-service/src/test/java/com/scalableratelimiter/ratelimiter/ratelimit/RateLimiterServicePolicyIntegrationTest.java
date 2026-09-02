package com.scalableratelimiter.ratelimiter.ratelimit;

import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitContext;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitPolicy;
import com.scalableratelimiter.ratelimiter.ratelimit.policy.RateLimitPolicyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServicePolicyIntegrationTest {

    @Mock
    private RateLimitPolicyResolver policyResolver;

    @Mock
    private RateLimitPolicy policy;

    @Test
    void checkRequest_delegatesToResolvedPolicy() {
        when(policyResolver.resolve(new RateLimitContext("alice"))).thenReturn(policy);
        when(policy.check(new RateLimitContext("alice"))).thenReturn(RateLimitDecision.ALLOWED);

        RateLimiterService service = new RateLimiterService(policyResolver);

        assertEquals(RateLimitDecision.ALLOWED, service.checkRequest("alice"));

        verify(policyResolver).resolve(new RateLimitContext("alice"));
        verify(policy).check(new RateLimitContext("alice"));
    }
}
