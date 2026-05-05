package com.example.ratelimiter.service;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class RateLimiterMetricsService {

    private final ConcurrentMap<String, ClientCounter> counters = new ConcurrentHashMap<>();

    public void recordDecision(String clientId, boolean allowed) {
        String safeClientId = (clientId == null || clientId.isBlank()) ? "unknown" : clientId;
        counters.computeIfAbsent(safeClientId, ignored -> new ClientCounter()).record(allowed);
    }

    public MetricsSnapshot snapshot() {
        Map<String, ClientSnapshot> clients = new TreeMap<>();
        for (Map.Entry<String, ClientCounter> entry : counters.entrySet()) {
            clients.put(entry.getKey(), entry.getValue().snapshot());
        }

        return new MetricsSnapshot(
                Instant.now().toString(),
                clients.size(),
                clients);
    }

    static final class ClientCounter {
        private final AtomicLong allowed = new AtomicLong();
        private final AtomicLong blocked = new AtomicLong();

        void record(boolean isAllowed) {
            if (isAllowed) {
                allowed.incrementAndGet();
            } else {
                blocked.incrementAndGet();
            }
        }

        ClientSnapshot snapshot() {
            long allowedCount = allowed.get();
            long blockedCount = blocked.get();
            return new ClientSnapshot(allowedCount, blockedCount, allowedCount + blockedCount);
        }
    }

    public record ClientSnapshot(long allowed, long blocked, long total) {
    }

    public record MetricsSnapshot(String timestamp, int totalClients, Map<String, ClientSnapshot> clients) {
    }
}

