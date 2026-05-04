# DistributedRateLimiter (Java)

This project mirrors the architecture of the C# repo (`DistributedRateLimiter`) step-by-step in Spring Boot.

## Migration Plan

- [x] Phase 0: Project rename and repo bootstrap
- [x] Phase 1: Local in-memory rate limiter (single instance)
- [ ] Phase 2: Redis-backed limiter (shared counters)
- [ ] Phase 3: Distributed hardening (multi-instance, resilience, metrics)

## Current Status (Phase 1)

- Project renamed to `DistributedRateLimiter`
- Spring app entrypoint renamed to `DistributedRateLimiterApplication`
- Maven artifact renamed to `distributed-rate-limiter`
- In-memory fixed-window rate limiter enabled for `/api/**`
- Exclusions in place for `/api/public`, `/test`, and Swagger docs
- 429 responses include `Retry-After` and `X-RateLimit-*` headers

## Phase 1 Config

```properties
ratelimiter.enabled=true
ratelimiter.limit=5
ratelimiter.window-seconds=60
ratelimiter.include-paths=/api/**
ratelimiter.exclude-paths=/api/public,/test,/swagger-ui/**,/v3/api-docs/**,/favicon.ico
```

## Run

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw test
```

