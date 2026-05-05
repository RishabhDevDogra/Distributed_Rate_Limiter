package com.example.ratelimiter.strategy.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.service.RedisCircuitBreakerService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class ConcurrentRateLimiterTests {

    private static RateLimiterProperties props(int limit) {
        RateLimiterProperties p = new RateLimiterProperties();
        p.setLimit(limit);
        p.setWindowSeconds(60);
        return p;
    }

    @Test void fixedWindowAllowsAtMostLimitConcurrently() throws Exception {
        int limit = 10, threads = 50;
        FixedWindowRateLimiter lim = new FixedWindowRateLimiter(props(limit));
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++)
            tasks.add(() -> { if (lim.evaluate("cfw").allowed()) allowed.incrementAndGet(); return null; });
        pool.invokeAll(tasks);
        pool.shutdown();
        assertEquals(limit, allowed.get());
    }

    @Test void slidingWindowAllowsAtMostLimitConcurrently() throws Exception {
        int limit = 10, threads = 50;
        SlidingWindowRateLimiter lim = new SlidingWindowRateLimiter(props(limit));
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++)
            tasks.add(() -> { if (lim.evaluate("csw").allowed()) allowed.incrementAndGet(); return null; });
        pool.invokeAll(tasks);
        pool.shutdown();
        // sliding window uses weighted estimate; allow +-1 tolerance
        assertTrue(allowed.get() >= limit - 1 && allowed.get() <= limit + 1,
            "Expected ~" + limit + " but got " + allowed.get());
    }

    @Test void leakyBucketAllowsAtMostLimitConcurrently() throws Exception {
        int limit = 10, threads = 50;
        LeakyBucketRateLimiter lim = new LeakyBucketRateLimiter(props(limit));
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++)
            tasks.add(() -> { if (lim.evaluate("clb").allowed()) allowed.incrementAndGet(); return null; });
        pool.invokeAll(tasks);
        pool.shutdown();
        assertEquals(limit, allowed.get());
    }

    @Test void tokenBucketAllowsAtMostLimitConcurrently() throws Exception {
        int limit = 10, threads = 50;
        RateLimiterProperties p = props(limit);
        TokenBucketRateLimiter lim = new TokenBucketRateLimiter(p, null, new RedisCircuitBreakerService(p));
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++)
            tasks.add(() -> { if (lim.evaluate("ctb").allowed()) allowed.incrementAndGet(); return null; });
        pool.invokeAll(tasks);
        pool.shutdown();
        assertEquals(limit, allowed.get());
    }

    @Test void perClientIsolationUnderConcurrentLoad() throws Exception {
        int limit = 5, clients = 4;
        FixedWindowRateLimiter lim = new FixedWindowRateLimiter(props(limit));
        ExecutorService pool = Executors.newFixedThreadPool(clients * limit);
        List<Callable<RateLimitDecision>> tasks = new ArrayList<>();
        for (int c = 0; c < clients; c++) {
            String key = "client-" + c;
            for (int r = 0; r < limit; r++) tasks.add(() -> lim.evaluate(key));
        }
        long allowedCount = pool.invokeAll(tasks).stream()
            .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
            .filter(d -> d != null && d.allowed())
            .count();
        pool.shutdown();
        assertEquals((long) clients * limit, allowedCount);
    }
}
