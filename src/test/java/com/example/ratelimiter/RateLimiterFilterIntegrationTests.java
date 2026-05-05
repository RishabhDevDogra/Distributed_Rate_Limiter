package com.example.ratelimiter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ratelimiter.strategy.inmemory.InMemoryFixedWindowRateLimiter;

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
class RateLimiterFilterIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryFixedWindowRateLimiter limiter;

    @BeforeEach
    void resetLimiter() {
        limiter.clearAll();
    }

    @Test
    void limitedEndpointBlocksAfterConfiguredLimit() throws Exception {
        mockMvc.perform(get("/api/limited/fixed-window").header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(get("/api/limited/fixed-window").header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        mockMvc.perform(get("/api/limited/fixed-window").header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"));
    }


    @Test
    void healthEndpointIsExcludedFromRateLimiting() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/health/live").header("X-Forwarded-For", "10.0.0.3"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void metricsEndpointIsExcludedFromRateLimiting() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/metrics").header("X-Forwarded-For", "10.0.0.4"))
                    .andExpect(status().isOk());
        }
    }
}

