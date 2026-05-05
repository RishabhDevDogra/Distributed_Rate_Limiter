package com.example.ratelimiter.strategy.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.service.RedisCircuitBreakerService;

class TokenBucketRateLimiterTests {

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(3);
        p.setWindowSeconds(60);
        limiter = new TokenBucketRateLimiter(p, null, new RedisCircuitBreakerService(p));
    }

    @Test void allowsFirstRequest() { assertTrue(limiter.evaluate("tb-1").allowed()); }

    @Test void allowsUpToCapacity() {
        assertTrue(limiter.evaluate("tb-2").allowed());
        assertTrue(limiter.evaluate("tb-2").allowed());
        assertTrue(limiter.evaluate("tb-2").allowed());
    }

    @Test void blocksWhenTokensExhausted() {
        limiter.evaluate("tb-3"); limiter.evaluate("tb-3"); limiter.evaluate("tb-3");
        assertFalse(limiter.evaluate("tb-3").allowed());
    }

    @Test void remainingDecrementsWithEachToken() {
        int r1 = limiter.evaluate("tb-4").remaining();
        int r2 = limiter.evaluate("tb-4").remaining();
        assertTrue(r1 >= r2);
    }

    @Test void remainingIsZeroAfterExhaustion() {
        limiter.evaluate("tb-5"); limiter.evaluate("tb-5"); limiter.evaluate("tb-5");
        assertEquals(0, limiter.evaluate("tb-5").remaining());
    }

    @Test void limitFieldMatchesCapacity() { assertEquals(3, limiter.evaluate("tb-6").limit()); }

    @Test void retryAfterPositiveWhenBlocked() {
        limiter.evaluate("tb-7"); limiter.evaluate("tb-7"); limiter.evaluate("tb-7");
        assertTrue(limiter.evaluate("tb-7").retryAfterSeconds() > 0);
    }

    @Test void retryAfterZeroWhenAllowed() { assertEquals(0, limiter.evaluate("tb-8").retryAfterSeconds()); }

    @Test void keysAreIsolated() {
        limiter.evaluate("tb-a"); limiter.evaluate("tb-a"); limiter.evaluate("tb-a");
        assertTrue(limiter.evaluate("tb-b").allowed());
    }

    @Test void clearAllResetsAllBuckets() {
        limiter.evaluate("tb-c"); limiter.evaluate("tb-c"); limiter.evaluate("tb-c");
        assertFalse(limiter.evaluate("tb-c").allowed());
        limiter.clearAll();
        assertTrue(limiter.evaluate("tb-c").allowed());
    }

    @Test void tokensRefillAfterDelay() throws Exception {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(2); p.setWindowSeconds(1);
        TokenBucketRateLimiter fast = new TokenBucketRateLimiter(p, null, new RedisCircuitBreakerService(p));
        assertTrue(fast.evaluate("tb-refill").allowed());
        assertTrue(fast.evaluate("tb-refill").allowed());
        assertFalse(fast.evaluate("tb-refill").allowed());
        Thread.sleep(600);
        assertTrue(fast.evaluate("tb-refill").allowed());
    }
}
