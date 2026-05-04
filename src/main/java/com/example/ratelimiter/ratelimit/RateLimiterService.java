package com.example.ratelimiter.ratelimit;

public interface RateLimiterService {
    RateLimitDecision evaluate(String key);
}

