package com.example.ratelimiter.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.service.RedisHealthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health", description = "Service liveness and readiness endpoints")
public class HealthController {

    private final RedisHealthService redisHealthService;
    private final RateLimiterProperties properties;

    public HealthController(RedisHealthService redisHealthService, RateLimiterProperties properties) {
        this.redisHealthService = redisHealthService;
        this.properties = properties;
    }

    @GetMapping("/health/live")
    @Operation(summary = "App liveness check - Is the application process running?", description = "Liveness probe endpoint")
    public Map<String, Object> live() {
        return Map.of(
                "status", "UP",
                "component", "application",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/health/ready")
    @Operation(summary = "App readiness check - Is the application ready to serve traffic? (checks Redis)", description = "Readiness probe endpoint")
    public Map<String, Object> ready() {
        boolean redisEnabled = redisHealthService.isRedisConfigured();
        boolean redisHealthy = redisHealthService.isRedisHealthy();
        String status = !redisEnabled || redisHealthy ? "UP" : "DEGRADED";

        return Map.of(
                "status", status,
                "redis", redisEnabled ? (redisHealthy ? "UP" : "DOWN") : "DISABLED",
                "fallback", properties.getRedis().isFallbackEnabled() ? "in-memory" : "disabled",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/health")
    @Operation(summary = "Detailed health check - Full diagnostics for all components (Redis, fallback metrics)", description = "Detailed system diagnostics endpoint")
    public Map<String, Object> health() {
        boolean redisEnabled = redisHealthService.isRedisConfigured();
        boolean redisHealthy = redisHealthService.isRedisHealthy();

        return Map.of(
                "status", "UP",
                "service", "DistributedRateLimiter",
                "mode", redisEnabled && redisHealthy ? "redis-primary" : "in-memory-fallback",
                "redis", redisEnabled ? (redisHealthy ? "UP" : "DOWN") : "DISABLED",
                "timestamp", Instant.now().toString());
    }
}

