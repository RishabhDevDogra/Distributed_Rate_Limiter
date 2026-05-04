package com.example.ratelimiter.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "Public endpoint - no limit";
    }

    @GetMapping("/limited")
    public ApiResponse limitedEndpoint() {
        return response("Default");
    }

    @GetMapping("/limited/token-bucket")
    public ApiResponse tokenBucketEndpoint() {
        return response("Token Bucket");
    }

    @GetMapping("/limited/fixed-window")
    public ApiResponse fixedWindowEndpoint() {
        return response("Fixed Window");
    }

    @GetMapping("/limited/sliding-window")
    public ApiResponse slidingWindowEndpoint() {
        return response("Sliding Window");
    }

    @GetMapping("/limited/leaky-bucket")
    public ApiResponse leakyBucketEndpoint() {
        return response("Leaky Bucket");
    }

    @PostMapping("/data")
    public String postData(@RequestBody String body) {
        return "Received: " + body;
    }

    private ApiResponse response(String algorithm) {
        return new ApiResponse("Request allowed", algorithm, Instant.now().toString());
    }

    public record ApiResponse(String message, String algorithm, String timestamp) {
    }
}