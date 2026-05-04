package com.example.ratelimiter.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class InMemoryTokenBucketRateLimiter implements LimiterStrategy {

    private final RateLimiterProperties properties;
    private final ConcurrentMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    public InMemoryTokenBucketRateLimiter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.TOKEN_BUCKET;
    }

    @Override
    public RateLimitDecision evaluate(String key) {
        int capacity = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        double refillPerSecond = (double) capacity / windowSeconds;
        long nowNanos = System.nanoTime();
        long nowEpochSeconds = Instant.now().getEpochSecond();

        BucketResult result = buckets.compute(key, (k, existing) -> {
            BucketState state = existing == null ? new BucketState(capacity, nowNanos) : existing;
            double elapsedSeconds = Math.max(0D, (nowNanos - state.lastRefillNanos) / 1_000_000_000D);
            state.availableTokens = Math.min(capacity, state.availableTokens + elapsedSeconds * refillPerSecond);

            boolean allowed = state.availableTokens >= 1D;
            if (allowed) {
                state.availableTokens -= 1D;
            }

            state.lastRefillNanos = nowNanos;
            state.lastAllowed = allowed;
            return state;
        }).toResult(capacity, refillPerSecond, nowEpochSeconds);

        return new RateLimitDecision(
                result.allowed,
                capacity,
                result.remaining,
                result.resetEpochSeconds,
                result.retryAfterSeconds);
    }

    public void clearAll() {
        buckets.clear();
    }

    private static final class BucketState {
        private double availableTokens;
        private long lastRefillNanos;
        private boolean lastAllowed;

        private BucketState(double availableTokens, long lastRefillNanos) {
            this.availableTokens = availableTokens;
            this.lastRefillNanos = lastRefillNanos;
        }

        private BucketResult toResult(int capacity, double refillPerSecond, long nowEpochSeconds) {
            int remaining = Math.max((int) Math.floor(availableTokens), 0);
            long retryAfterSeconds = 0;
            if (!lastAllowed) {
                double tokensMissing = Math.max(1D - availableTokens, 0D);
                retryAfterSeconds = Math.max((long) Math.ceil(tokensMissing / refillPerSecond), 1L);
            }

            double secondsToFull = Math.max((capacity - availableTokens) / refillPerSecond, 0D);
            long resetEpochSeconds = nowEpochSeconds + (long) Math.ceil(secondsToFull);

            return new BucketResult(lastAllowed, remaining, resetEpochSeconds, retryAfterSeconds);
        }
    }

    private record BucketResult(boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    }
}

