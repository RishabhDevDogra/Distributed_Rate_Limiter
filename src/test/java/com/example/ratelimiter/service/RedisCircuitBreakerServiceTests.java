package com.example.ratelimiter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.ratelimiter.config.RateLimiterProperties;

class RedisCircuitBreakerServiceTests {

    @Test
    void circuitOpensOnFailureAndThenHalfOpensAfterWindow() throws Exception {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getRedis().setCircuitOpenSeconds(1);

        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);

        assertTrue(service.allowRequest());
        service.recordFailure();

        assertEquals(RedisCircuitBreakerService.CircuitState.OPEN, service.getState());
        assertFalse(service.allowRequest());

        Thread.sleep(1100);
        assertTrue(service.allowRequest());
        assertEquals(RedisCircuitBreakerService.CircuitState.HALF_OPEN, service.getState());
    }

    @Test
    void circuitClosesAfterSuccessfulProbe() {
        RateLimiterProperties properties = new RateLimiterProperties();
        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);

        service.recordFailure();
        service.recordSuccess();

        assertEquals(RedisCircuitBreakerService.CircuitState.CLOSED, service.getState());
        assertTrue(service.allowRequest());
    }
}

