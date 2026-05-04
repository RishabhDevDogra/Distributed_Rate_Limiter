package com.example.ratelimiter.ratelimit;

public interface RateLimiterService {
    default RateLimitDecision evaluate(String key) {
        return evaluate(key, null);
    }

    RateLimitDecision evaluate(String key, LimiterStrategyType strategyType);
}

