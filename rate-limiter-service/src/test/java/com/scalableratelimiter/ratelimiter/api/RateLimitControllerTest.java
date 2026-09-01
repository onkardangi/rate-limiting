package com.scalableratelimiter.ratelimiter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void check_returnsAllowedTrue_whenUnderLimit() throws Exception {
        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void check_returnsAllowedFalse_afterLimitExhausted() throws Exception {
        String userId = "http-limit-test-user";

        for (int i = 0; i < 100; i++) {
            mockMvc.perform(post("/api/rate-limit/check")
                            .header(RateLimitController.USER_ID_HEADER, userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        mockMvc.perform(post("/api/rate-limit/check")
                        .header(RateLimitController.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }
}
