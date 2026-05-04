package com.example.ratelimiter.ratelimit;

public enum LimiterStrategyType {
    FIXED_WINDOW,
    TOKEN_BUCKET,
    SLIDING_WINDOW,
    LEAKY_BUCKET
}

