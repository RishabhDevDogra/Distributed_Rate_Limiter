package com.example.ratelimiter.ratelimit;

public interface LimiterStrategy {
    LimiterStrategyType type();

    RateLimitDecision evaluate(String key);
}

