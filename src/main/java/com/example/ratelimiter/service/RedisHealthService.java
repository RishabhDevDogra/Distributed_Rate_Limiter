package com.example.ratelimiter.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.service.RedisCircuitBreakerService.CircuitState;

@Service
public class RedisHealthService {

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisCircuitBreakerService circuitBreakerService;

    public RedisHealthService(
            RateLimiterProperties properties,
            StringRedisTemplate redisTemplate,
            RedisCircuitBreakerService circuitBreakerService) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.circuitBreakerService = circuitBreakerService;
    }

    public boolean isRedisConfigured() {
        return properties.getRedis().isEnabled();
    }

    public boolean isRedisHealthy() {
        if (!isRedisConfigured()) {
            return false;
        }

        // When breaker is OPEN, skip ping to keep health responses fast.
        if (circuitBreakerService.getState() == CircuitState.OPEN) {
            return false;
        }

        try {
            int timeoutMs = Math.max(properties.getRedis().getTimeoutMs(), 1);
            String pong = CompletableFuture
                    .supplyAsync(() -> redisTemplate.execute((RedisCallback<String>) connection -> connection.ping()))
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();
            boolean healthy = "PONG".equalsIgnoreCase(pong);
            if (healthy) {
                circuitBreakerService.recordSuccess();
            }
            return healthy;
        } catch (RuntimeException ex) {
            circuitBreakerService.recordFailure();
            return false;
        }
    }
}

