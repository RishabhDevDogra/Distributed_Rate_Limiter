package com.example.ratelimiter.web.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitDecision;
import com.example.ratelimiter.service.RateLimiterService;
import com.example.ratelimiter.strategy.LimiterStrategyType;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimiterProperties properties;
    private final RateLimiterService limiterService;
    private final ClientKeyResolver clientKeyResolver;

    public RateLimitFilter(
            RateLimiterProperties properties,
            RateLimiterService limiterService,
            ClientKeyResolver clientKeyResolver) {
        this.properties = properties;
        this.limiterService = limiterService;
        this.clientKeyResolver = clientKeyResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        if (matches(properties.getExcludePaths(), path)) {
            return true;
        }

        if (properties.getIncludePaths().isEmpty()) {
            return false;
        }

        return !matches(properties.getIncludePaths(), path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientId = clientKeyResolver.resolveClientId(request);
        String key = clientId + "|" + request.getMethod() + "|" + path;
        LimiterStrategyType strategyType = resolveStrategyType(path);

        RateLimitDecision decision = limiterService.evaluate(key, strategyType);
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.getWriter().write("Rate limit exceeded. Please retry later.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimiterStrategyType resolveStrategyType(String path) {
        if (path == null) {
            return null;
        }

        if (path.startsWith("/api/limited/token-bucket")) {
            return LimiterStrategyType.TOKEN_BUCKET;
        }
        if (path.startsWith("/api/limited/fixed-window")) {
            return LimiterStrategyType.FIXED_WINDOW;
        }
        if (path.startsWith("/api/limited/sliding-window")) {
            return LimiterStrategyType.SLIDING_WINDOW;
        }
        if (path.startsWith("/api/limited/leaky-bucket")) {
            return LimiterStrategyType.LEAKY_BUCKET;
        }

        return null;
    }

    private boolean matches(Iterable<String> patterns, String path) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}



