package com.example.ratelimiter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.strategy.algorithm.TokenBucketRateLimiter;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ratelimiter.enabled=true",
        "ratelimiter.strategy-type=fixed-window",
        "ratelimiter.limit=2",
        "ratelimiter.window-seconds=60",
        "ratelimiter.include-paths=/api/**",
        "ratelimiter.exclude-paths=/health/**,/api/metrics,/swagger-ui/**,/v3/api-docs/**,/favicon.ico"
})
class RateLimiterTokenBucketIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void clearState() {
        limiter.clearAll();
    }

    @Test
    void tokenBucketStrategyIsAppliedViaConfiguration() throws Exception {
        mockMvc.perform(get("/api/limited/token-bucket").header("X-Forwarded-For", "10.10.10.10"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/limited/token-bucket").header("X-Forwarded-For", "10.10.10.10"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/limited/token-bucket").header("X-Forwarded-For", "10.10.10.10"))
                .andExpect(status().isTooManyRequests());
    }
}

