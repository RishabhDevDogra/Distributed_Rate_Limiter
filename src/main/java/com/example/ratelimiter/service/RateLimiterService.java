package com.example.ratelimiter.service;

import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.strategy.LimiterStrategyType;

public interface RateLimiterService {
    default RateLimitDecision evaluate(String key) {
        return evaluate(key, null);
    }

    RateLimitDecision evaluate(String key, LimiterStrategyType strategyType);
}



