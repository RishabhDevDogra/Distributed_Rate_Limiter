package com.example.ratelimiter.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "System", description = "Service status endpoints")
public class HealthController {

    @GetMapping({"/health", "/test"})
    @Operation(summary = "Service status", description = "Quick status endpoint to verify the API is running")
    public String health() {
        return "Rate limiter service running 🚀";
    }
}
