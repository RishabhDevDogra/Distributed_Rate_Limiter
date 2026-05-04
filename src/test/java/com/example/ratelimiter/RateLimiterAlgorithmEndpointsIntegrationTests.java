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

import com.example.ratelimiter.ratelimit.InMemoryFixedWindowRateLimiter;
import com.example.ratelimiter.ratelimit.InMemoryLeakyBucketRateLimiter;
import com.example.ratelimiter.ratelimit.InMemorySlidingWindowRateLimiter;
import com.example.ratelimiter.ratelimit.InMemoryTokenBucketRateLimiter;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ratelimiter.enabled=true",
        "ratelimiter.strategy-type=fixed-window",
        "ratelimiter.limit=2",
        "ratelimiter.window-seconds=60",
        "ratelimiter.include-paths=/api/**",
        "ratelimiter.exclude-paths=/api/public,/test,/swagger-ui/**,/v3/api-docs/**,/favicon.ico"
})
class RateLimiterAlgorithmEndpointsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryFixedWindowRateLimiter fixedWindow;

    @Autowired
    private InMemoryTokenBucketRateLimiter tokenBucket;

    @Autowired
    private InMemorySlidingWindowRateLimiter slidingWindow;

    @Autowired
    private InMemoryLeakyBucketRateLimiter leakyBucket;

    @BeforeEach
    void clearAllState() {
        fixedWindow.clearAll();
        tokenBucket.clearAll();
        slidingWindow.clearAll();
        leakyBucket.clearAll();
    }

    @Test
    void fixedWindowEndpointUsesFixedWindowLimiter() throws Exception {
        assertThirdRequestIsBlocked("/api/limited/fixed-window", "10.0.1.1");
    }

    @Test
    void tokenBucketEndpointUsesTokenBucketLimiter() throws Exception {
        assertThirdRequestIsBlocked("/api/limited/token-bucket", "10.0.1.2");
    }

    @Test
    void slidingWindowEndpointUsesSlidingWindowLimiter() throws Exception {
        assertThirdRequestIsBlocked("/api/limited/sliding-window", "10.0.1.3");
    }

    @Test
    void leakyBucketEndpointUsesLeakyBucketLimiter() throws Exception {
        assertThirdRequestIsBlocked("/api/limited/leaky-bucket", "10.0.1.4");
    }

    private void assertThirdRequestIsBlocked(String endpoint, String ip) throws Exception {
        mockMvc.perform(get(endpoint).header("X-Forwarded-For", ip)).andExpect(status().isOk());
        mockMvc.perform(get(endpoint).header("X-Forwarded-For", ip)).andExpect(status().isOk());
        mockMvc.perform(get(endpoint).header("X-Forwarded-For", ip)).andExpect(status().isTooManyRequests());
    }
}

