package com.example.ratelimiter.ratelimit.web;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientKeyResolver {

    public String resolveClientId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int firstComma = forwardedFor.indexOf(',');
            if (firstComma > -1) {
                return forwardedFor.substring(0, firstComma).trim();
            }
            return forwardedFor.trim();
        }

        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null ? "unknown" : remoteAddress;
    }
}


