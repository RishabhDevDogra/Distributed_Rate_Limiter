package com.example.ratelimiter.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class InMemoryFixedWindowRateLimiter implements LimiterStrategy {

    private final RateLimiterProperties properties;
    private final ConcurrentMap<String, CounterWindow> counters = new ConcurrentHashMap<>();

    public InMemoryFixedWindowRateLimiter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.FIXED_WINDOW;
    }

    @Override
    public RateLimitDecision evaluate(String key) {
        int safeLimit = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long windowStart = nowEpochSeconds - (nowEpochSeconds % windowSeconds);

        CounterWindow window = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStartEpochSeconds != windowStart) {
                return new CounterWindow(windowStart, 1);
            }
            existing.usedTokens += 1;
            return existing;
        });

        int usedTokens = window.usedTokens;
        boolean allowed = usedTokens <= safeLimit;
        int remaining = Math.max(safeLimit - usedTokens, 0);
        long resetEpochSeconds = windowStart + windowSeconds;
        long retryAfterSeconds = allowed ? 0 : Math.max(resetEpochSeconds - nowEpochSeconds, 1);

        return new RateLimitDecision(allowed, safeLimit, remaining, resetEpochSeconds, retryAfterSeconds);
    }

    public void clearAll() {
        counters.clear();
    }

    private static final class CounterWindow {
        private final long windowStartEpochSeconds;
        private int usedTokens;

        private CounterWindow(long windowStartEpochSeconds, int usedTokens) {
            this.windowStartEpochSeconds = windowStartEpochSeconds;
            this.usedTokens = usedTokens;
        }
    }
}

