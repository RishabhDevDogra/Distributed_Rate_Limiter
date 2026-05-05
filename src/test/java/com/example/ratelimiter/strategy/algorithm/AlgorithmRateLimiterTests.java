package com.example.ratelimiter.strategy.algorithm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.service.RedisCircuitBreakerService;

class AlgorithmRateLimiterTests {

    @Test
    void tokenBucketRefillsAfterDelay() throws Exception {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setLimit(2);
        properties.setWindowSeconds(1);

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                properties,
                null,
                new RedisCircuitBreakerService(properties));
        String key = "token-bucket-key";

        assertTrue(limiter.evaluate(key).allowed());
        assertTrue(limiter.evaluate(key).allowed());
        assertFalse(limiter.evaluate(key).allowed());

        Thread.sleep(600);
        assertTrue(limiter.evaluate(key).allowed());
    }

    @Test
    void slidingWindowBlocksWhenLimitReachedInSameWindow() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setLimit(2);
        properties.setWindowSeconds(2);

        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(properties);
        String key = "sliding-window-key";

        assertTrue(limiter.evaluate(key).allowed());
        assertTrue(limiter.evaluate(key).allowed());
        assertFalse(limiter.evaluate(key).allowed());
    }

    @Test
    void leakyBucketDrainsAfterDelay() throws Exception {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setLimit(2);
        properties.setWindowSeconds(1);

        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(properties);
        String key = "leaky-bucket-key";

        assertTrue(limiter.evaluate(key).allowed());
        assertTrue(limiter.evaluate(key).allowed());
        assertFalse(limiter.evaluate(key).allowed());

        Thread.sleep(600);
        assertTrue(limiter.evaluate(key).allowed());
    }
}




