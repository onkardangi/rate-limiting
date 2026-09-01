package com.scalableratelimiter.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header: X-User-Id"));
    }

    @Test
    void blankUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header: X-User-Id"));
    }

    @Test
    void allowedRequest_reachesProductEndpoint() throws Exception {
        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.name").value("Example Product"));
    }

    @Test
    void rateLimitedRequest_returnsTooManyRequests() throws Exception {
        String userId = "rate-limit-test-user";

        for (int i = 0; i < RateLimiterService.MAX_REQUESTS_PER_MINUTE; i++) {
            mockMvc.perform(get("/api/products/123")
                            .header(RateLimitFilter.USER_ID_HEADER, userId))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"));
    }
}
