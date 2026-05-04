package com.example.ratelimiter.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long resetEpochSeconds,
        long retryAfterSeconds) {
}

