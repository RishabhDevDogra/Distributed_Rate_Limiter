# DistributedRateLimiter (Java)

This project mirrors the architecture of the C# repo (`DistributedRateLimiter`) step-by-step in Spring Boot.

## Migration Plan

- [x] Phase 0: Project rename and repo bootstrap
- [ ] Phase 1: Local in-memory rate limiter (single instance)
- [ ] Phase 2: Redis-backed limiter (shared counters)
- [ ] Phase 3: Distributed hardening (multi-instance, resilience, metrics)

## Current Status (Phase 0)

- Project renamed to `DistributedRateLimiter`
- Spring app entrypoint renamed to `DistributedRateLimiterApplication`
- Maven artifact renamed to `distributed-rate-limiter`

## Run

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw test
```

