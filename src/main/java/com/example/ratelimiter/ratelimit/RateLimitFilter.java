package com.example.ratelimiter.ratelimit;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

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
        String clientId = clientKeyResolver.resolveClientId(request);
        String key = clientId + "|" + request.getMethod() + "|" + request.getRequestURI();

        RateLimitDecision decision = limiterService.evaluate(key);
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

    private boolean matches(Iterable<String> patterns, String path) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}

