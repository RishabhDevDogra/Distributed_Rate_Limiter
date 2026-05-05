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

    @Test
    void circuitStartsInClosedState() {
        RateLimiterProperties properties = new RateLimiterProperties();
        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);
        assertEquals(RedisCircuitBreakerService.CircuitState.CLOSED, service.getState());
    }

    @Test
    void successOnClosedStateKeepsCircuitClosed() {
        RateLimiterProperties properties = new RateLimiterProperties();
        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);
        service.recordSuccess();
        service.recordSuccess();
        assertEquals(RedisCircuitBreakerService.CircuitState.CLOSED, service.getState());
        assertTrue(service.allowRequest());
    }

    @Test
    void openCircuitBlocksAllRequests() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getRedis().setCircuitOpenSeconds(60);
        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);
        service.recordFailure();
        assertFalse(service.allowRequest());
        assertFalse(service.allowRequest());
        assertFalse(service.allowRequest());
    }

    @Test
    void halfOpenTransitionAllowsSingleProbe() throws Exception {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getRedis().setCircuitOpenSeconds(1);
        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);
        service.recordFailure();
        assertFalse(service.allowRequest());
        Thread.sleep(1100);
        assertTrue(service.allowRequest());
        assertEquals(RedisCircuitBreakerService.CircuitState.HALF_OPEN, service.getState());
    }

    @Test
    void failureOnHalfOpenReopensCircuit() throws Exception {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getRedis().setCircuitOpenSeconds(1);
        RedisCircuitBreakerService service = new RedisCircuitBreakerService(properties);
        service.recordFailure();
        Thread.sleep(1100);
        service.allowRequest(); // probe -> HALF_OPEN
        service.recordFailure(); // probe failed -> re-OPEN
        assertEquals(RedisCircuitBreakerService.CircuitState.OPEN, service.getState());
    }

}
