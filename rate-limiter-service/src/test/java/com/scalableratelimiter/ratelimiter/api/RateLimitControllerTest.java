package com.scalableratelimiter.ratelimiter.api;

import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStore;
import com.scalableratelimiter.ratelimiter.ratelimit.RateLimitStateStoreException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitStateStore stateStore;

    @Test
    void check_returnsAllowed_whenUnderLimit() throws Exception {
        when(stateStore.increment(anyString(), any(Duration.class))).thenReturn(1L);

        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"));
    }

    @Test
    void check_returnsRateLimited_afterLimitExhausted() throws Exception {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenReturn((long) 101);

        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("RATE_LIMITED"));
    }

    @Test
    void check_returnsUnavailable_whenStateStoreFails() throws Exception {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, "alice"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.decision").value("UNAVAILABLE"));
    }

    @Test
    void infrastructureFailure_isNotReportedAsRateLimited() throws Exception {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, "alice"))
                .andExpect(jsonPath("$.decision").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.decision").value(org.hamcrest.Matchers.not("RATE_LIMITED")));
    }

    @Test
    void infrastructureFailure_isNotReportedAsAllowed() throws Exception {
        when(stateStore.increment(anyString(), any(Duration.class)))
                .thenThrow(new RateLimitStateStoreException("Redis unavailable"));

        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, "alice"))
                .andExpect(jsonPath("$.decision").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.decision").value(org.hamcrest.Matchers.not("ALLOWED")));
    }
}
