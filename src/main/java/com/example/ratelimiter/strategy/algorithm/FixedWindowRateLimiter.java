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
public class FixedWindowRateLimiter implements LimiterStrategy {

    private static final RedisScript<List> FIXED_WINDOW_SCRIPT = buildScript();

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisCircuitBreakerService circuitBreaker;
    private final ConcurrentMap<String, CounterWindow> counters = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RateLimiterProperties properties) {
        this(properties, null, new RedisCircuitBreakerService(properties));
    }

    @Autowired
    public FixedWindowRateLimiter(
            RateLimiterProperties properties,
            @Nullable StringRedisTemplate redisTemplate,
            RedisCircuitBreakerService circuitBreaker) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.FIXED_WINDOW;
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
        int safeLimit = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        long nowEpochSeconds = Instant.now().getEpochSecond();
        String redisKey = properties.getRedis().getKeyPrefix() + ":fixed-window:" + key;

        List result = redisTemplate.execute(
                FIXED_WINDOW_SCRIPT,
                List.of(redisKey),
                String.valueOf(safeLimit),
                String.valueOf(windowSeconds),
                String.valueOf(nowEpochSeconds));

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Unexpected Redis fixed-window script result");
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        int remaining = (int) Math.max(toLong(result.get(1)), 0L);
        long resetEpochSeconds = toLong(result.get(2));
        long retryAfterSeconds = Math.max(toLong(result.get(3)), 0L);

        return new RateLimitDecision(allowed, safeLimit, remaining, resetEpochSeconds, retryAfterSeconds);
    }

    private RateLimitDecision evaluateInMemory(String key) {
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
                + "local windowStart = now - (now % windowSeconds) "
                + "local reset = windowStart + windowSeconds "
                + "local data = redis.call('HMGET', key, 'windowStart', 'count') "
                + "local existingWindowStart = tonumber(data[1]) "
                + "local count = tonumber(data[2]) "
                + "if existingWindowStart == nil or existingWindowStart ~= windowStart then "
                + "  count = 1 "
                + "else "
                + "  count = (count or 0) + 1 "
                + "end "
                + "local allowed = 0 "
                + "if count <= limit then allowed = 1 end "
                + "local remaining = math.max(limit - count, 0) "
                + "local retryAfter = 0 "
                + "if allowed == 0 then retryAfter = math.max(reset - now, 1) end "
                + "redis.call('HMSET', key, 'windowStart', windowStart, 'count', count) "
                + "redis.call('EXPIRE', key, windowSeconds * 2) "
                + "return {allowed, remaining, reset, retryAfter}";

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);
        return script;
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




