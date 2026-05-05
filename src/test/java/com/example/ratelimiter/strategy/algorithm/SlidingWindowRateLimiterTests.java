package com.example.ratelimiter.strategy.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.ratelimiter.config.RateLimiterProperties;

class SlidingWindowRateLimiterTests {

    private SlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(3);
        p.setWindowSeconds(60);
        limiter = new SlidingWindowRateLimiter(p);
    }

    @Test void allowsFirstRequest() { assertTrue(limiter.evaluate("sw-1").allowed()); }

    @Test void allowsUpToLimit() {
        assertTrue(limiter.evaluate("sw-2").allowed());
        assertTrue(limiter.evaluate("sw-2").allowed());
        assertTrue(limiter.evaluate("sw-2").allowed());
    }

    @Test void blocksAfterLimitExceeded() {
        limiter.evaluate("sw-3"); limiter.evaluate("sw-3"); limiter.evaluate("sw-3");
        assertFalse(limiter.evaluate("sw-3").allowed());
    }

    @Test void remainingDecrementsWithEachRequest() {
        int r1 = limiter.evaluate("sw-4").remaining();
        int r2 = limiter.evaluate("sw-4").remaining();
        assertTrue(r1 >= r2);
    }

    @Test void remainingIsZeroWhenLimitReached() {
        limiter.evaluate("sw-5"); limiter.evaluate("sw-5"); limiter.evaluate("sw-5");
        assertEquals(0, limiter.evaluate("sw-5").remaining());
    }

    @Test void limitFieldMatchesConfiguration() { assertEquals(3, limiter.evaluate("sw-6").limit()); }

    @Test void retryAfterPositiveWhenBlocked() {
        limiter.evaluate("sw-7"); limiter.evaluate("sw-7"); limiter.evaluate("sw-7");
        assertTrue(limiter.evaluate("sw-7").retryAfterSeconds() > 0);
    }

    @Test void retryAfterZeroWhenAllowed() { assertEquals(0, limiter.evaluate("sw-8").retryAfterSeconds()); }

    @Test void keysAreIsolated() {
        limiter.evaluate("sw-a"); limiter.evaluate("sw-a"); limiter.evaluate("sw-a");
        assertTrue(limiter.evaluate("sw-b").allowed());
    }

    @Test void clearAllResetsWindowState() {
        limiter.evaluate("sw-c"); limiter.evaluate("sw-c"); limiter.evaluate("sw-c");
        assertFalse(limiter.evaluate("sw-c").allowed());
        limiter.clearAll();
        assertTrue(limiter.evaluate("sw-c").allowed());
    }
}
