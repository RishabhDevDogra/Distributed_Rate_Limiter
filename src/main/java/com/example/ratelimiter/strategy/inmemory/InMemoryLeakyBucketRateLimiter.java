package com.example.ratelimiter.strategy.inmemory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.strategy.LimiterStrategy;
import com.example.ratelimiter.strategy.LimiterStrategyType;

@Service
public class InMemoryLeakyBucketRateLimiter implements LimiterStrategy {

    private final RateLimiterProperties properties;
    private final ConcurrentMap<String, LeakyState> buckets = new ConcurrentHashMap<>();

    public InMemoryLeakyBucketRateLimiter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.LEAKY_BUCKET;
    }

    @Override
    public RateLimitDecision evaluate(String key) {
        int capacity = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        double leakRatePerSecond = (double) capacity / windowSeconds;
        long nowNanos = System.nanoTime();
        long nowEpochSeconds = Instant.now().getEpochSecond();

        LeakyResult result = buckets.compute(key, (k, existing) -> {
            LeakyState state = existing == null ? new LeakyState(0D, nowNanos) : existing;
            double elapsedSeconds = Math.max(0D, (nowNanos - state.lastUpdatedNanos) / 1_000_000_000D);
            state.level = Math.max(0D, state.level - (elapsedSeconds * leakRatePerSecond));

            boolean allowed = state.level + 1D <= capacity;
            if (allowed) {
                state.level += 1D;
            }

            state.lastUpdatedNanos = nowNanos;
            state.lastAllowed = allowed;
            return state;
        }).toResult(capacity, leakRatePerSecond, nowEpochSeconds);

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

    private static final class LeakyState {
        private double level;
        private long lastUpdatedNanos;
        private boolean lastAllowed;

        private LeakyState(double level, long lastUpdatedNanos) {
            this.level = level;
            this.lastUpdatedNanos = lastUpdatedNanos;
        }

        private LeakyResult toResult(int capacity, double leakRatePerSecond, long nowEpochSeconds) {
            int remaining = Math.max((int) Math.floor(capacity - level), 0);
            long retryAfterSeconds = 0;
            if (!lastAllowed) {
                double overflow = Math.max((level + 1D) - capacity, 0D);
                retryAfterSeconds = Math.max((long) Math.ceil(overflow / leakRatePerSecond), 1L);
            }

            long resetEpochSeconds = nowEpochSeconds + (long) Math.ceil(Math.max(level / leakRatePerSecond, 0D));
            return new LeakyResult(lastAllowed, remaining, resetEpochSeconds, retryAfterSeconds);
        }
    }

    private record LeakyResult(boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    }
}



