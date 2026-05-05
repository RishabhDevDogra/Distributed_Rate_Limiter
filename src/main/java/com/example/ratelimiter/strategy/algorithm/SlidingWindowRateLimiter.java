package com.example.ratelimiter.strategy.algorithm;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.service.RedisCircuitBreakerService;
import com.example.ratelimiter.strategy.LimiterStrategy;
import com.example.ratelimiter.strategy.LimiterStrategyType;

@Service
public class SlidingWindowRateLimiter implements LimiterStrategy {

    private static final RedisScript<List> SLIDING_WINDOW_SCRIPT = buildScript();

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisCircuitBreakerService circuitBreaker;
    private final ConcurrentMap<String, SlidingWindowState> windows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(RateLimiterProperties properties) {
        this(properties, null, new RedisCircuitBreakerService(properties));
    }

    @Autowired
    public SlidingWindowRateLimiter(
            RateLimiterProperties properties,
            @Nullable StringRedisTemplate redisTemplate,
            RedisCircuitBreakerService circuitBreaker) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.SLIDING_WINDOW;
    }

    @Override
    public RateLimitDecision evaluate(String key) {
        if (properties.getRedis().isEnabled() && redisTemplate != null) {
            if (!circuitBreaker.allowRequest()) {
                if (!properties.getRedis().isFallbackEnabled()) {
                    throw new IllegalStateException("Redis circuit breaker is OPEN");
                }
                return evaluateInMemory(key);
            }

            try {
                int timeoutMs = Math.max(properties.getRedis().getTimeoutMs(), 1);
                RateLimitDecision decision = CompletableFuture
                        .supplyAsync(() -> evaluateRedis(key))
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .join();
                circuitBreaker.recordSuccess();
                return decision;
            } catch (RuntimeException ex) {
                circuitBreaker.recordFailure();
                if (!properties.getRedis().isFallbackEnabled()) {
                    throw ex;
                }
            }
        } else if (properties.getRedis().isEnabled() && !properties.getRedis().isFallbackEnabled()) {
            throw new IllegalStateException("Redis is enabled but no Redis template is available");
        }

        return evaluateInMemory(key);
    }

    private RateLimitDecision evaluateRedis(String key) {
        int limit = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        long nowEpochSeconds = Instant.now().getEpochSecond();
        String redisKey = properties.getRedis().getKeyPrefix() + ":sliding-window:" + key;

        List result = redisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                List.of(redisKey),
                String.valueOf(limit),
                String.valueOf(windowSeconds),
                String.valueOf(nowEpochSeconds));

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Unexpected Redis sliding-window script result");
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        int remaining = (int) Math.max(toLong(result.get(1)), 0L);
        long resetEpochSeconds = toLong(result.get(2));
        long retryAfterSeconds = Math.max(toLong(result.get(3)), 0L);

        return new RateLimitDecision(allowed, limit, remaining, resetEpochSeconds, retryAfterSeconds);
    }

    private RateLimitDecision evaluateInMemory(String key) {
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

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static RedisScript<List> buildScript() {
        String lua = "local limit = tonumber(ARGV[1]) "
                + "local windowSeconds = tonumber(ARGV[2]) "
                + "local now = tonumber(ARGV[3]) "
                + "local key = KEYS[1] "
                + "local currentWindowStart = now - (now % windowSeconds) "
                + "local reset = currentWindowStart + windowSeconds "
                + "local data = redis.call('HMGET', key, 'windowStart', 'previousCount', 'currentCount') "
                + "local windowStart = tonumber(data[1]) "
                + "local previousCount = tonumber(data[2]) or 0 "
                + "local currentCount = tonumber(data[3]) or 0 "
                + "if windowStart == nil then "
                + "  windowStart = currentWindowStart "
                + "elseif currentWindowStart ~= windowStart then "
                + "  if currentWindowStart == windowStart + windowSeconds then "
                + "    previousCount = currentCount "
                + "  else "
                + "    previousCount = 0 "
                + "  end "
                + "  currentCount = 0 "
                + "  windowStart = currentWindowStart "
                + "end "
                + "local elapsedInWindow = now - currentWindowStart "
                + "local previousWeight = (windowSeconds - elapsedInWindow) / windowSeconds "
                + "local estimatedCount = (previousCount * previousWeight) + currentCount "
                + "local allowed = 0 "
                + "if estimatedCount + 1 <= limit then "
                + "  allowed = 1 "
                + "  currentCount = currentCount + 1 "
                + "  estimatedCount = estimatedCount + 1 "
                + "end "
                + "local remaining = math.max(math.floor(limit - estimatedCount), 0) "
                + "local retryAfter = 0 "
                + "if allowed == 0 then retryAfter = math.max(reset - now, 1) end "
                + "redis.call('HMSET', key, 'windowStart', windowStart, 'previousCount', previousCount, 'currentCount', currentCount) "
                + "redis.call('EXPIRE', key, windowSeconds * 3) "
                + "return {allowed, remaining, reset, retryAfter}";

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);
        return script;
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




