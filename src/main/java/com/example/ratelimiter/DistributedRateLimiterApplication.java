package com.example.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.ratelimiter.ratelimit.config.RateLimiterProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimiterProperties.class)
public class DistributedRateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedRateLimiterApplication.class, args);
    }

}

