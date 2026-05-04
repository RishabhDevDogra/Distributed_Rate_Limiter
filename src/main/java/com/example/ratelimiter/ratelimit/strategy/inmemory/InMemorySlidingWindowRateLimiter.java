package com.example.ratelimiter.ratelimit.strategy.inmemory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.example.ratelimiter.ratelimit.config.RateLimiterProperties;
import com.example.ratelimiter.ratelimit.model.RateLimitDecision;
import com.example.ratelimiter.ratelimit.strategy.LimiterStrategy;
import com.example.ratelimiter.ratelimit.strategy.LimiterStrategyType;

@Service
public class InMemorySlidingWindowRateLimiter implements LimiterStrategy {

    private final RateLimiterProperties properties;
    private final ConcurrentMap<String, SlidingWindowState> windows = new ConcurrentHashMap<>();

    public InMemorySlidingWindowRateLimiter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.SLIDING_WINDOW;
    }

    @Override
    public RateLimitDecision evaluate(String key) {
        int limit = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long currentWindowStart = nowEpochSeconds - (nowEpochSeconds % windowSeconds);

        SlidingResult result = windows.compute(key, (k, existing) -> {
            SlidingWindowState state = existing == null ? new SlidingWindowState(currentWindowStart) : existing;
            state.rotateIfNeeded(currentWindowStart, windowSeconds);

            long elapsedInWindow = nowEpochSeconds - currentWindowStart;
            double previousWeight = (double) (windowSeconds - elapsedInWindow) / windowSeconds;
            double estimatedCount = (state.previousCount * previousWeight) + state.currentCount;

            boolean allowed = estimatedCount + 1D <= limit;
            if (allowed) {
                state.currentCount += 1;
                estimatedCount += 1D;
            }

            int remaining = Math.max((int) Math.floor(limit - estimatedCount), 0);
            long resetEpochSeconds = currentWindowStart + windowSeconds;
            long retryAfterSeconds = allowed ? 0 : Math.max(resetEpochSeconds - nowEpochSeconds, 1);

            state.lastResult = new SlidingResult(allowed, remaining, resetEpochSeconds, retryAfterSeconds);
            return state;
        }).lastResult;

        return new RateLimitDecision(result.allowed, limit, result.remaining, result.resetEpochSeconds, result.retryAfterSeconds);
    }

    public void clearAll() {
        windows.clear();
    }

    private static final class SlidingWindowState {
        private long windowStartEpochSeconds;
        private int previousCount;
        private int currentCount;
        private SlidingResult lastResult;

        private SlidingWindowState(long windowStartEpochSeconds) {
            this.windowStartEpochSeconds = windowStartEpochSeconds;
        }

        private void rotateIfNeeded(long currentWindowStart, int windowSeconds) {
            if (currentWindowStart == windowStartEpochSeconds) {
                return;
            }

            if (currentWindowStart == windowStartEpochSeconds + windowSeconds) {
                previousCount = currentCount;
            } else {
                previousCount = 0;
            }

            currentCount = 0;
            windowStartEpochSeconds = currentWindowStart;
        }
    }

    private record SlidingResult(boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    }
}


