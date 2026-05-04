package com.example.ratelimiter.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Rate Limiter", description = "Algorithm endpoints and sample API routes")
public class ApiController {

    @GetMapping("/public")
    @Operation(summary = "Public endpoint", description = "No rate limiting is applied to this route")
    public String publicEndpoint() {
        return "Public endpoint - no limit";
    }

    @GetMapping("/limited")
    @Operation(summary = "Default limiter endpoint", description = "Uses the default configured limiter strategy")
    public ApiResponse limitedEndpoint() {
        return response("Default");
    }

    @GetMapping("/limited/token-bucket")
    @Operation(summary = "Token bucket limiter", description = "Applies token bucket algorithm per client IP")
    public ApiResponse tokenBucketEndpoint() {
        return response("Token Bucket");
    }

    @GetMapping("/limited/fixed-window")
    @Operation(summary = "Fixed window limiter", description = "Applies fixed window algorithm per client IP")
    public ApiResponse fixedWindowEndpoint() {
        return response("Fixed Window");
    }

    @GetMapping("/limited/sliding-window")
    @Operation(summary = "Sliding window limiter", description = "Applies sliding window algorithm per client IP")
    public ApiResponse slidingWindowEndpoint() {
        return response("Sliding Window");
    }

    @GetMapping("/limited/leaky-bucket")
    @Operation(summary = "Leaky bucket limiter", description = "Applies leaky bucket algorithm per client IP")
    public ApiResponse leakyBucketEndpoint() {
        return response("Leaky Bucket");
    }

    @PostMapping("/data")
    @Operation(summary = "Sample POST endpoint", description = "Echo endpoint to test POST traffic with rate limiting")
    public String postData(@RequestBody String body) {
        return "Received: " + body;
    }

    private ApiResponse response(String algorithm) {
        return new ApiResponse("Request allowed", algorithm, Instant.now().toString());
    }

    public record ApiResponse(String message, String algorithm, String timestamp) {
    }
}