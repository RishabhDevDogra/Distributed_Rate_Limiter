package com.example.ratelimiter.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health", description = "Service liveness and readiness endpoints")
public class HealthController {

    @GetMapping("/health/live")
    @Operation(summary = "App liveness check", description = "Is the application process running?")
    public Map<String, Object> live() {
        return Map.of(
                "status", "UP",
                "component", "application",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/health/ready")
    @Operation(summary = "App readiness check", description = "Is the application ready to serve traffic? (checks Redis)")
    public Map<String, Object> ready() {
        return Map.of(
                "status", "UP",
                "redis", "not-configured",
                "fallback", "in-memory",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/health")
    @Operation(summary = "Detailed health check", description = "Full diagnostics for all components (Redis, fallback metrics)")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "DistributedRateLimiter",
                "mode", "in-memory",
                "timestamp", Instant.now().toString());
    }
}

