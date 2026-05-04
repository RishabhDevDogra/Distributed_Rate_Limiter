package com.example.ratelimiter.strategy;

import com.example.ratelimiter.model.RateLimitDecision;

public interface LimiterStrategy {
    LimiterStrategyType type();

    RateLimitDecision evaluate(String key);
}



