package com.example.ratelimiter.strategy.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.ratelimiter.config.RateLimiterProperties;

class FixedWindowRateLimiterTests {

    private FixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(3);
        p.setWindowSeconds(60);
        limiter = new FixedWindowRateLimiter(p);
    }

    @Test void allowsFirstRequest()  { assertTrue(limiter.evaluate("fw-1").allowed()); }

    @Test void allowsUpToLimit() {
        assertTrue(limiter.evaluate("fw-2").allowed());
        assertTrue(limiter.evaluate("fw-2").allowed());
        assertTrue(limiter.evaluate("fw-2").allowed());
    }

    @Test void blocksOnLimitPlusOne() {
        limiter.evaluate("fw-3"); limiter.evaluate("fw-3"); limiter.evaluate("fw-3");
        assertFalse(limiter.evaluate("fw-3").allowed());
    }

    @Test void remainingDecrementsWithEachRequest() {
        assertEquals(2, limiter.evaluate("fw-4").remaining());
        assertEquals(1, limiter.evaluate("fw-4").remaining());
        assertEquals(0, limiter.evaluate("fw-4").remaining());
    }

    @Test void remainingIsZeroWhenBlocked() {
        limiter.evaluate("fw-5"); limiter.evaluate("fw-5"); limiter.evaluate("fw-5");
        assertEquals(0, limiter.evaluate("fw-5").remaining());
    }

    @Test void limitFieldMatchesConfiguration() { assertEquals(3, limiter.evaluate("fw-6").limit()); }

    @Test void retryAfterPositiveWhenBlocked() {
        limiter.evaluate("fw-7"); limiter.evaluate("fw-7"); limiter.evaluate("fw-7");
        assertTrue(limiter.evaluate("fw-7").retryAfterSeconds() > 0);
    }

    @Test void retryAfterZeroWhenAllowed() { assertEquals(0, limiter.evaluate("fw-8").retryAfterSeconds()); }

    @Test void resetEpochIsInFuture() {
        assertTrue(limiter.evaluate("fw-9").resetEpochSeconds() > System.currentTimeMillis() / 1000L);
    }

    @Test void keysAreIsolated() {
        limiter.evaluate("fw-a"); limiter.evaluate("fw-a"); limiter.evaluate("fw-a");
        assertTrue(limiter.evaluate("fw-b").allowed());
    }

    @Test void clearAllResetsCounters() {
        limiter.evaluate("fw-c"); limiter.evaluate("fw-c"); limiter.evaluate("fw-c");
        assertFalse(limiter.evaluate("fw-c").allowed());
        limiter.clearAll();
        assertTrue(limiter.evaluate("fw-c").allowed());
    }
}
