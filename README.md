# DistributedRateLimiter (Java)

This project mirrors the architecture of the C# repo (`DistributedRateLimiter`) step-by-step in Spring Boot.

## Migration Plan

- [x] Phase 0: Project rename and repo bootstrap
- [x] Phase 1: Local in-memory rate limiter (single instance)
- [x] Phase 2A: Pluggable limiter architecture
- [x] Phase 2B: In-memory multi-algorithm endpoints
- [ ] Phase 2C: Redis-backed limiter (shared counters)
- [ ] Phase 3: Distributed hardening (multi-instance, resilience, metrics)

## Current Status (Phase 2B)

- Project renamed to `DistributedRateLimiter`
- Spring app entrypoint renamed to `DistributedRateLimiterApplication`
- Maven artifact renamed to `distributed-rate-limiter`
- In-memory algorithms available: `fixed-window`, `token-bucket`, `sliding-window`, `leaky-bucket`
- Endpoint-driven algorithm routing under `/api/limited/*` to match the .NET workflow
- Exclusions in place for `/api/public`, `/health` (and `/test` alias), and Swagger docs
- 429 responses include `Retry-After` and `X-RateLimit-*` headers

## Endpoints

- `GET /api/limited/token-bucket`
- `GET /api/limited/fixed-window`
- `GET /api/limited/sliding-window`
- `GET /api/limited/leaky-bucket`

## Quick Demo

```bash
for endpoint in token-bucket fixed-window sliding-window leaky-bucket; do
  echo "== $endpoint =="
  for i in {1..3}; do
	curl -s -o /dev/null -w "request $i -> HTTP %{http_code}\n" \
	  "http://localhost:8080/api/limited/$endpoint"
  done
done
```

## Phase 1 Config

```properties
ratelimiter.enabled=true
ratelimiter.strategy-type=fixed-window
ratelimiter.limit=5
ratelimiter.window-seconds=60
ratelimiter.include-paths=/api/**
ratelimiter.exclude-paths=/api/public,/health,/test,/swagger-ui/**,/v3/api-docs/**,/favicon.ico
```

## Code Organization

- `com.example.ratelimiter.controller` - HTTP controllers (`RateLimitController`, `HealthController`)
- `com.example.ratelimiter.filter` - request-level rate limit filter and client resolver
- `com.example.ratelimiter.service` - service orchestration (`RateLimiterService`, `PluggableRateLimiterService`)
- `com.example.ratelimiter.strategy` - strategy contracts/enums
- `com.example.ratelimiter.strategy.inmemory` - in-memory algorithm implementations
- `com.example.ratelimiter.config` and `.model` - shared properties and decision models

## Run

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw test
```

