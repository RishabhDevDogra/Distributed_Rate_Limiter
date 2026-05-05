package com.example.ratelimiter.strategy.algorithm;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.service.RedisCircuitBreakerService;
import com.example.ratelimiter.strategy.LimiterStrategy;
import com.example.ratelimiter.strategy.LimiterStrategyType;

@Service
public class TokenBucketRateLimiter implements LimiterStrategy {

    private static final RedisScript<List> TOKEN_BUCKET_SCRIPT = buildScript();

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisCircuitBreakerService circuitBreaker;
    private final ConcurrentMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(
            RateLimiterProperties properties,
            StringRedisTemplate redisTemplate,
            RedisCircuitBreakerService circuitBreaker) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public LimiterStrategyType type() {
        return LimiterStrategyType.TOKEN_BUCKET;
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
                // Avoid per-request async future allocation/churn; rely on Redis/client timeout configuration.
                RateLimitDecision decision = evaluateRedis(key);
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
        double refillPerSecond = (double) capacity / windowSeconds;
        double refillPerMillisecond = refillPerSecond / 1000D;
        long nowMillis = System.currentTimeMillis();
        long nowEpochSeconds = nowMillis / 1000L;
        String redisKey = properties.getRedis().getKeyPrefix() + ":token-bucket:" + key;
        long ttlMillis = Math.max(windowSeconds * 2000L, 1000L);

        List result;
        try {
            result = redisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(capacity),
                    String.valueOf(refillPerMillisecond),
                    String.valueOf(nowMillis),
                    String.valueOf(ttlMillis));
        } catch (RedisConnectionFailureException ex) {
            throw ex;
        }

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Unexpected Redis token bucket script result");
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        int remaining = (int) Math.max(toLong(result.get(1)), 0L);
        long retryAfterMs = Math.max(toLong(result.get(2)), 0L);
        long resetMs = Math.max(toLong(result.get(3)), 0L);

        long retryAfterSeconds = allowed ? 0L : Math.max((long) Math.ceil(retryAfterMs / 1000D), 1L);
        long resetEpochSeconds = nowEpochSeconds + (long) Math.ceil(resetMs / 1000D);

        return new RateLimitDecision(allowed, capacity, remaining, resetEpochSeconds, retryAfterSeconds);
    }

    private RateLimitDecision evaluateInMemory(String key) {
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

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static RedisScript<List> buildScript() {
        String lua = "local capacity = tonumber(ARGV[1]) "
                + "local refillPerMs = tonumber(ARGV[2]) "
                + "local nowMs = tonumber(ARGV[3]) "
                + "local ttlMs = tonumber(ARGV[4]) "
                + "local key = KEYS[1] "
                + "local data = redis.call('HMGET', key, 'tokens', 'timestamp') "
                + "local tokens = tonumber(data[1]) "
                + "local timestamp = tonumber(data[2]) "
                + "if tokens == nil then tokens = capacity end "
                + "if timestamp == nil then timestamp = nowMs end "
                + "local elapsed = math.max(0, nowMs - timestamp) "
                + "tokens = math.min(capacity, tokens + (elapsed * refillPerMs)) "
                + "local allowed = 0 "
                + "local retryAfterMs = 0 "
                + "if tokens >= 1 then "
                + "  allowed = 1 "
                + "  tokens = tokens - 1 "
                + "else "
                + "  retryAfterMs = math.ceil((1 - tokens) / refillPerMs) "
                + "end "
                + "local resetMs = math.ceil((capacity - tokens) / refillPerMs) "
                + "redis.call('HMSET', key, 'tokens', tokens, 'timestamp', nowMs) "
                + "redis.call('PEXPIRE', key, ttlMs) "
                + "local remaining = math.max(0, math.floor(tokens)) "
                + "return {allowed, remaining, retryAfterMs, resetMs}";

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);
        return script;
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




