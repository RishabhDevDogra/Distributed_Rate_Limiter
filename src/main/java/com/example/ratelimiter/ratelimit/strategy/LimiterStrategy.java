package com.example.ratelimiter.ratelimit.strategy;

import com.example.ratelimiter.ratelimit.model.RateLimitDecision;

public interface LimiterStrategy {
    LimiterStrategyType type();

    RateLimitDecision evaluate(String key);
}


