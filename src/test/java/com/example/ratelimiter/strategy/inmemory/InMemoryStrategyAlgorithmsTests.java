package com.example.ratelimiter.strategy.inmemory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.ratelimiter.config.RateLimiterProperties;

class InMemoryStrategyAlgorithmsTests {

    @Test
    void tokenBucketRefillsAfterDelay() throws Exception {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setLimit(2);
        properties.setWindowSeconds(1);

        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(properties);
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

        InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter(properties);
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

        InMemoryLeakyBucketRateLimiter limiter = new InMemoryLeakyBucketRateLimiter(properties);
        String key = "leaky-bucket-key";

        assertTrue(limiter.evaluate(key).allowed());
        assertTrue(limiter.evaluate(key).allowed());
        assertFalse(limiter.evaluate(key).allowed());

        Thread.sleep(600);
        assertTrue(limiter.evaluate(key).allowed());
    }
}



