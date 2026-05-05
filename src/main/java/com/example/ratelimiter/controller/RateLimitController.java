package com.example.ratelimiter.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "RateLimiter", description = "Rate limiting algorithm endpoints")
public class RateLimitController {


    @GetMapping("/limited/token-bucket")
    @Operation(summary = "Token Bucket algorithm - Distributed (Redis with in-memory fallback) for burst-friendly limiting", description = "Applies token bucket algorithm per client IP")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request allowed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = "{\"message\":\"Request allowed\",\"algorithm\":\"Token Bucket\",\"timestamp\":\"2026-05-04T16:00:00Z\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Rate limit exceeded. Please retry later.")))
    })
    public ApiResponse tokenBucketEndpoint() {
        return response("Token Bucket");
    }

    @GetMapping("/limited/fixed-window")
    @Operation(summary = "Fixed Window algorithm - Simple counter-based approach with periodic resets", description = "Applies fixed window algorithm per client IP")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request allowed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = "{\"message\":\"Request allowed\",\"algorithm\":\"Fixed Window\",\"timestamp\":\"2026-05-04T16:00:00Z\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Rate limit exceeded. Please retry later.")))
    })
    public ApiResponse fixedWindowEndpoint() {
        return response("Fixed Window");
    }

    @GetMapping("/limited/sliding-window")
    @Operation(summary = "Sliding Window algorithm - Most accurate, tracks exact request timestamps in rolling window", description = "Applies sliding window algorithm per client IP")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request allowed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = "{\"message\":\"Request allowed\",\"algorithm\":\"Sliding Window\",\"timestamp\":\"2026-05-04T16:00:00Z\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Rate limit exceeded. Please retry later.")))
    })
    public ApiResponse slidingWindowEndpoint() {
        return response("Sliding Window");
    }

    @GetMapping("/limited/leaky-bucket")
    @Operation(summary = "Leaky Bucket algorithm - Traffic shaping with constant outflow rate for smooth throughput", description = "Applies leaky bucket algorithm per client IP")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request allowed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = "{\"message\":\"Request allowed\",\"algorithm\":\"Leaky Bucket\",\"timestamp\":\"2026-05-04T16:00:00Z\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Rate limit exceeded. Please retry later.")))
    })
    public ApiResponse leakyBucketEndpoint() {
        return response("Leaky Bucket");
    }


    private ApiResponse response(String algorithm) {
        return new ApiResponse("Request allowed", algorithm, Instant.now().toString());
    }

    public record ApiResponse(String message, String algorithm, String timestamp) {
    }
}

