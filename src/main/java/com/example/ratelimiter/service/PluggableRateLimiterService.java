package com.example.ratelimiter.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.strategy.LimiterStrategy;
import com.example.ratelimiter.strategy.LimiterStrategyType;

@Service
public class PluggableRateLimiterService implements RateLimiterService {

    private final RateLimiterProperties properties;
    private final Map<LimiterStrategyType, LimiterStrategy> strategies;

    public PluggableRateLimiterService(RateLimiterProperties properties, List<LimiterStrategy> strategyList) {
        this.properties = properties;
        this.strategies = new EnumMap<>(LimiterStrategyType.class);
        for (LimiterStrategy strategy : strategyList) {
            this.strategies.put(strategy.type(), strategy);
        }
    }

    @Override
    public RateLimitDecision evaluate(String key, LimiterStrategyType strategyType) {
        LimiterStrategyType selectedType = strategyType == null ? properties.getStrategyType() : strategyType;
        LimiterStrategy strategy = strategies.get(selectedType);
        if (strategy == null) {
            throw new IllegalStateException("No limiter strategy registered for: " + selectedType);
        }
        return strategy.evaluate(key);
    }
}



