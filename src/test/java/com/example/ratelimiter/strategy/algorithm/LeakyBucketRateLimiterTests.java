package com.example.ratelimiter.strategy.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.ratelimiter.config.RateLimiterProperties;

class LeakyBucketRateLimiterTests {

    private LeakyBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(3);
        p.setWindowSeconds(60);
        limiter = new LeakyBucketRateLimiter(p);
    }

    @Test void allowsFirstRequest() { assertTrue(limiter.evaluate("lb-1").allowed()); }

    @Test void allowsUpToCapacity() {
        assertTrue(limiter.evaluate("lb-2").allowed());
        assertTrue(limiter.evaluate("lb-2").allowed());
        assertTrue(limiter.evaluate("lb-2").allowed());
    }

    @Test void blocksWhenBucketFull() {
        limiter.evaluate("lb-3"); limiter.evaluate("lb-3"); limiter.evaluate("lb-3");
        assertFalse(limiter.evaluate("lb-3").allowed());
    }

    @Test void remainingDecrementsAsLevelRises() {
        int r1 = limiter.evaluate("lb-4").remaining();
        int r2 = limiter.evaluate("lb-4").remaining();
        assertTrue(r1 >= r2);
    }

    @Test void remainingIsZeroWhenFull() {
        limiter.evaluate("lb-5"); limiter.evaluate("lb-5"); limiter.evaluate("lb-5");
        assertEquals(0, limiter.evaluate("lb-5").remaining());
    }

    @Test void limitFieldMatchesCapacity() { assertEquals(3, limiter.evaluate("lb-6").limit()); }

    @Test void retryAfterPositiveWhenBlocked() {
        limiter.evaluate("lb-7"); limiter.evaluate("lb-7"); limiter.evaluate("lb-7");
        assertTrue(limiter.evaluate("lb-7").retryAfterSeconds() > 0);
    }

    @Test void retryAfterZeroWhenAllowed() { assertEquals(0, limiter.evaluate("lb-8").retryAfterSeconds()); }

    @Test void keysAreIsolated() {
        limiter.evaluate("lb-a"); limiter.evaluate("lb-a"); limiter.evaluate("lb-a");
        assertTrue(limiter.evaluate("lb-b").allowed());
    }

    @Test void clearAllResetsBuckets() {
        limiter.evaluate("lb-c"); limiter.evaluate("lb-c"); limiter.evaluate("lb-c");
        assertFalse(limiter.evaluate("lb-c").allowed());
        limiter.clearAll();
        assertTrue(limiter.evaluate("lb-c").allowed());
    }

    @Test void bucketDrainsAfterDelay() throws Exception {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(2); p.setWindowSeconds(1);
        LeakyBucketRateLimiter fast = new LeakyBucketRateLimiter(p);
        assertTrue(fast.evaluate("lb-drain").allowed());
        assertTrue(fast.evaluate("lb-drain").allowed());
        assertFalse(fast.evaluate("lb-drain").allowed());
        Thread.sleep(600);
        assertTrue(fast.evaluate("lb-drain").allowed());
    }
}
