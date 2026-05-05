package com.example.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.service.RateLimiterMetricsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "RateLimiter", description = "Rate limiter endpoints and metrics")
public class MetricsController {

    private final RateLimiterMetricsService metricsService;

    public MetricsController(RateLimiterMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Rate limiter metrics", description = "View request statistics per client (allowed/blocked counts)")
    public RateLimiterMetricsService.MetricsSnapshot metrics() {
        return metricsService.snapshot();
    }
}

