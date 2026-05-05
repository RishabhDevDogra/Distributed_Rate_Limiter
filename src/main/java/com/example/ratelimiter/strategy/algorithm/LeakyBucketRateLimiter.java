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
public class LeakyBucketRateLimiter implements LimiterStrategy {

    private static final RedisScript<List> LEAKY_BUCKET_SCRIPT = buildScript();

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisCircuitBreakerService circuitBreaker;
    private final ConcurrentMap<String, LeakyState> buckets = new ConcurrentHashMap<>();

    public LeakyBucketRateLimiter(RateLimiterProperties properties) {
        this(properties, null, new RedisCircuitBreakerService(properties));
    }

    @Autowired
    public LeakyBucketRateLimiter(
            RateLimiterProperties properties,
            @Nullable StringRedisTemplate redisTemplate,
            RedisCircuitBreakerService circuitBreaker) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.LEAKY_BUCKET;
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
        int capacity = Math.max(properties.getLimit(), 1);
        int windowSeconds = Math.max(properties.getWindowSeconds(), 1);
        double leakRatePerMs = ((double) capacity / windowSeconds) / 1000D;
        long nowMillis = System.currentTimeMillis();
        long nowEpochSeconds = Instant.now().getEpochSecond();
        String redisKey = properties.getRedis().getKeyPrefix() + ":leaky-bucket:" + key;
        long ttlMillis = Math.max(windowSeconds * 3000L, 1000L);

        List result = redisTemplate.execute(
                LEAKY_BUCKET_SCRIPT,
                List.of(redisKey),
                String.valueOf(capacity),
                String.valueOf(leakRatePerMs),
                String.valueOf(nowMillis),
                String.valueOf(nowEpochSeconds),
                String.valueOf(ttlMillis));

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Unexpected Redis leaky-bucket script result");
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        int remaining = (int) Math.max(toLong(result.get(1)), 0L);
        long resetEpochSeconds = toLong(result.get(2));
        long retryAfterSeconds = Math.max(toLong(result.get(3)), 0L);

        return new RateLimitDecision(allowed, capacity, remaining, resetEpochSeconds, retryAfterSeconds);
    }

    private RateLimitDecision evaluateInMemory(String key) {
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

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static RedisScript<List> buildScript() {
        String lua = "local capacity = tonumber(ARGV[1]) "
                + "local leakRatePerMs = tonumber(ARGV[2]) "
                + "local nowMs = tonumber(ARGV[3]) "
                + "local nowEpochSeconds = tonumber(ARGV[4]) "
                + "local ttlMs = tonumber(ARGV[5]) "
                + "local key = KEYS[1] "
                + "local data = redis.call('HMGET', key, 'level', 'lastUpdatedMs') "
                + "local level = tonumber(data[1]) "
                + "local lastUpdatedMs = tonumber(data[2]) "
                + "if level == nil then level = 0 end "
                + "if lastUpdatedMs == nil then lastUpdatedMs = nowMs end "
                + "local elapsedMs = math.max(0, nowMs - lastUpdatedMs) "
                + "level = math.max(0, level - (elapsedMs * leakRatePerMs)) "
                + "local allowed = 0 "
                + "if level + 1 <= capacity then "
                + "  allowed = 1 "
                + "  level = level + 1 "
                + "end "
                + "local remaining = math.max(math.floor(capacity - level), 0) "
                + "local retryAfterSeconds = 0 "
                + "if allowed == 0 then "
                + "  local overflow = math.max((level + 1) - capacity, 0) "
                + "  local retryAfterMs = math.ceil(overflow / leakRatePerMs) "
                + "  retryAfterSeconds = math.max(math.ceil(retryAfterMs / 1000), 1) "
                + "end "
                + "local resetMs = math.ceil(math.max(level / leakRatePerMs, 0)) "
                + "local resetEpochSeconds = nowEpochSeconds + math.ceil(resetMs / 1000) "
                + "redis.call('HMSET', key, 'level', level, 'lastUpdatedMs', nowMs) "
                + "redis.call('PEXPIRE', key, ttlMs) "
                + "return {allowed, remaining, resetEpochSeconds, retryAfterSeconds}";

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);
        return script;
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




