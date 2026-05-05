package com.example.ratelimiter.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;

@Service
public class RedisHealthService {

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;

    public RedisHealthService(RateLimiterProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public boolean isRedisConfigured() {
        return properties.getRedis().isEnabled();
    }

    public boolean isRedisHealthy() {
        if (!isRedisConfigured()) {
            return false;
        }

        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (DataAccessException ex) {
            return false;
        }
    }
}

