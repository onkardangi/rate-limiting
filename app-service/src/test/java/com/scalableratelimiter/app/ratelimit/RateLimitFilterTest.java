package com.scalableratelimiter.app.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitClient rateLimitClient;

    @Test
    void missingUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header: X-User-Id"));

        verify(rateLimitClient, never()).checkRateLimit(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void blankUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header: X-User-Id"));

        verify(rateLimitClient, never()).checkRateLimit(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void allowedRequest_reachesProductEndpoint() throws Exception {
        when(rateLimitClient.checkRateLimit("alice")).thenReturn(true);

        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.name").value("Example Product"));

        verify(rateLimitClient).checkRateLimit("alice");
    }

    @Test
    void rateLimitedRequest_returnsTooManyRequests() throws Exception {
        when(rateLimitClient.checkRateLimit("alice")).thenReturn(false);

        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, "alice"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"));

        verify(rateLimitClient).checkRateLimit("alice");
    }

    @Test
    void rateLimitedRequest_doesNotReachProductEndpoint() throws Exception {
        when(rateLimitClient.checkRateLimit("alice")).thenReturn(false);

        mockMvc.perform(get("/api/products/123")
                        .header(RateLimitFilter.USER_ID_HEADER, "alice"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist());

        verify(rateLimitClient).checkRateLimit("alice");
    }
}
