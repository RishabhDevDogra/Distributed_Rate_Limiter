package com.example.ratelimiter.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;

@Service
public class RedisCircuitBreakerService {

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final RateLimiterProperties properties;
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicLong openedAtEpochMillis = new AtomicLong(0L);

    public RedisCircuitBreakerService(RateLimiterProperties properties) {
        this.properties = properties;
    }

    public boolean allowRequest() {
        CircuitState current = state.get();
        if (current == CircuitState.CLOSED) {
            return true;
        }

        long nowMillis = System.currentTimeMillis();
        long openWindowMillis = Math.max(properties.getRedis().getCircuitOpenSeconds(), 1) * 1000L;
        long openedAt = openedAtEpochMillis.get();

        if (current == CircuitState.OPEN && nowMillis - openedAt >= openWindowMillis) {
            state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN);
            return true;
        }

        return current == CircuitState.HALF_OPEN;
    }

    public void recordSuccess() {
        state.set(CircuitState.CLOSED);
        openedAtEpochMillis.set(0L);
    }

    public void recordFailure() {
        openedAtEpochMillis.set(System.currentTimeMillis());
        state.set(CircuitState.OPEN);
    }

    public CircuitState getState() {
        return state.get();
    }
}

