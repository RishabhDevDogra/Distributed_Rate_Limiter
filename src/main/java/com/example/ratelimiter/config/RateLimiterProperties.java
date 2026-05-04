package com.example.ratelimiter.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.ratelimiter.strategy.LimiterStrategyType;

@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {
    private boolean enabled = true;
    private LimiterStrategyType strategyType = LimiterStrategyType.FIXED_WINDOW;
    private int limit = 5;
    private int windowSeconds = 60;
    private List<String> includePaths = new ArrayList<>(List.of("/api/**"));
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/api/public",
            "/test",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/favicon.ico"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLimit() {
        return limit;
    }

    public LimiterStrategyType getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(LimiterStrategyType strategyType) {
        this.strategyType = strategyType;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}



