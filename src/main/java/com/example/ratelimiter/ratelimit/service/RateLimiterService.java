package com.example.ratelimiter.ratelimit.service;

import com.example.ratelimiter.ratelimit.model.RateLimitDecision;
import com.example.ratelimiter.ratelimit.strategy.LimiterStrategyType;

public interface RateLimiterService {
    default RateLimitDecision evaluate(String key) {
        return evaluate(key, null);
    }

    RateLimitDecision evaluate(String key, LimiterStrategyType strategyType);
}


