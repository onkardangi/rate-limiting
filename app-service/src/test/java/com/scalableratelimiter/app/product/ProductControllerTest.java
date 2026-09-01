package com.scalableratelimiter.app.product;

import com.scalableratelimiter.app.ratelimit.RateLimitDecision;
import com.scalableratelimiter.app.ratelimit.RateLimitClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitClient rateLimitClient;

    @Test
    void getProduct_returnsExpectedResponse_whenRateLimiterAllows() throws Exception {
        when(rateLimitClient.checkRateLimit("alice")).thenReturn(RateLimitDecision.ALLOWED);

        mockMvc.perform(get("/api/products/123")
                        .header("X-User-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.name").value("Example Product"));

        verify(rateLimitClient).checkRateLimit("alice");
    }
}
