# DistributedRateLimiter (Java / Spring Boot)

![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen)
![Redis](https://img.shields.io/badge/Redis-7%2B-red)
![Maven](https://img.shields.io/badge/Build-Maven-orange)

Distributed API rate limiter in Java (Spring Boot) with four algorithms, atomic Redis Lua updates, and circuit-breaker-based in-memory fallback. Local benchmark evidence in this repository shows ~5.2k-5.8k req/sec with p99 ~74-85 ms (20,000 requests, concurrency 200).

This repository is a standalone Java project.

## Table of Contents

- [Quick Start (2 minutes)](#quick-start-2-minutes)
- [Highlights](#highlights)
- [API Endpoints](#api-endpoints)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Local Setup](#local-setup)
- [Quick Verification](#quick-verification)
- [Benchmarking](#benchmarking)
- [Performance Results](#performance-results)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Status](#status)

## Quick Start (2 minutes)

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter

# 1) Start Redis
brew services start redis
redis-cli ping

# 2) Run app in Redis-primary mode
./mvnw spring-boot:run -Dspring-boot.run.arguments="--ratelimiter.redis.enabled=true --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6379"
```

In another terminal:

```bash
curl -s http://localhost:8080/health
curl -i http://localhost:8080/api/limited/token-bucket
curl -s http://localhost:8080/api/metrics
```

## Highlights

- Four algorithm endpoints: token bucket, fixed window, sliding window, leaky bucket
- Redis-first execution with Lua scripts for atomic updates
- Automatic fallback to in-memory state when Redis is unavailable
- Circuit breaker (`CLOSED`, `OPEN`, `HALF_OPEN`) to avoid repeated slow failures
- Standard rate-limit response headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`
- Health + readiness + metrics endpoints for runtime visibility
- Swagger/OpenAPI documentation

## About This Project

This project demonstrates how to build a distributed, fault-tolerant rate limiter in Java with strong consistency on the Redis path and graceful degradation when Redis is unavailable.

- **Primary path:** Redis + Lua for atomic, race-condition-safe updates
- **Resilience path:** circuit breaker + in-memory fallback
- **Scope:** per-client key limiting across token bucket, fixed window, sliding window, and leaky bucket strategies
- **Operational support:** health, readiness, metrics, and standard `X-RateLimit-*` headers

## API Endpoints

### Health

- `GET /health/live`
- `GET /health/ready`
- `GET /health`

### Rate-limited Endpoints

- `GET /api/limited/token-bucket`
- `GET /api/limited/fixed-window`
- `GET /api/limited/sliding-window`
- `GET /api/limited/leaky-bucket`

### Metrics and Docs

- `GET /api/metrics`
- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`

## Architecture

Distributed, fault-tolerant rate limiter with high availability and strong consistency guarantees:

```text
Incoming Request
    ↓
RateLimitFilter
  • resolve client key
  • map endpoint -> algorithm
    ↓
PluggableRateLimiterService
  • dispatch to selected strategy
    ↓
Primary Path: Redis + Lua Script (atomic update)
  • uses configured timeout (default: 500ms)
  • on timeout/error/open breaker -> fallback path
    ↓
Fallback Path: Circuit Breaker -> In-Memory Strategy
    ↓
Response
  • X-RateLimit-Limit / X-RateLimit-Remaining / X-RateLimit-Reset
  • Retry-After (when blocked)
  • HTTP 200 or HTTP 429
```

**Guarantees (implementation-level):**

- ✅ **Atomic operations** - Lua scripts avoid race conditions on Redis updates
- ✅ **Automatic failover** - circuit breaker routes traffic to fallback on Redis failures
- ✅ **Thread-safe local fallback** - in-memory strategies use concurrent-safe structures
- ✅ **Horizontal-readiness** - per-key isolation and Redis-backed distributed mode

**Performance note:** benchmark evidence in this repository currently shows ~5.2k-5.8k RPS with local-loopback p99 in the ~74-85ms range (20000 requests, concurrency 200, local MacBook). Use dedicated load infrastructure before publishing stricter SLA claims.

## Configuration

Main settings in `src/main/resources/application.properties`:

```properties
spring.application.name=DistributedRateLimiter

ratelimiter.enabled=true
ratelimiter.strategy-type=fixed-window
ratelimiter.limit=5
ratelimiter.window-seconds=60
ratelimiter.include-paths=/api/**
ratelimiter.exclude-paths=/health/**,/api/metrics,/swagger-ui/**,/v3/api-docs/**,/favicon.ico

ratelimiter.redis.enabled=false
ratelimiter.redis.fallback-enabled=true
ratelimiter.redis.key-prefix=ratelimiter
ratelimiter.redis.timeout-ms=500
ratelimiter.redis.circuit-open-seconds=5

# Enable when Redis is available
# spring.data.redis.host=localhost
# spring.data.redis.port=6379
```

## Local Setup

### Prerequisites

- Java 21
- Maven Wrapper (`./mvnw`)
- Redis 7+ (required for Redis-primary mode)

### Start Redis

```bash
brew services start redis
redis-cli ping
```

Expected:

```text
PONG
```

### Run App

In-memory mode:

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
./mvnw spring-boot:run
```

Redis-primary mode:

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
./mvnw spring-boot:run -Dspring-boot.run.arguments="--ratelimiter.redis.enabled=true --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6379"
```

### Run Tests

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
./mvnw clean test
```

Current suite status: `67` tests, all passing.

## Quick Verification

```bash
curl -s http://localhost:8080/health/live
curl -s http://localhost:8080/health/ready
curl -s http://localhost:8080/health
curl -i http://localhost:8080/api/limited/token-bucket
curl -s http://localhost:8080/api/metrics
```

## Benchmarking

Harness: `benchmarks/load_benchmark.py`

See also: `benchmarks/README.md` and `benchmarks/results/`.

Example single run:

```bash
python3 benchmarks/load_benchmark.py \
  --url http://localhost:8080/api/limited/token-bucket \
  --requests 20000 \
  --concurrency 200 \
  --timeout 5 \
  --output benchmarks/results/token-bucket-local.json
```

## Performance Results

Environment: Java 21, Spring Boot 4.0.6, local MacBook, loopback HTTP, 20000 requests at concurrency 200.

### In-memory mode (`redis=DISABLED`)

| Endpoint | RPS | p50 ms | p95 ms | p99 ms | Success |
| --- | ---: | ---: | ---: | ---: | ---: |
| token-bucket | 5502.13 | 29.644 | 59.544 | 80.586 | 20000/20000 |
| fixed-window | 5736.00 | 21.978 | 56.881 | 74.896 | 20000/20000 |
| sliding-window | 5691.29 | 28.200 | 58.381 | 75.653 | 20000/20000 |
| leaky-bucket | 5779.19 | 22.222 | 56.625 | 74.146 | 20000/20000 |

### Redis-primary mode (`redis=UP`, `mode=redis-primary`)

| Endpoint | RPS | p50 ms | p95 ms | p99 ms | Success |
| --- | ---: | ---: | ---: | ---: | ---: |
| token-bucket | 5310.93 | 35.637 | 65.571 | 85.271 | 20000/20000 |
| fixed-window | 5342.61 | 26.866 | 62.281 | 82.823 | 20000/20000 |
| sliding-window | 5267.40 | 25.795 | 59.806 | 76.378 | 20000/20000 |
| leaky-bucket | 5310.60 | 22.687 | 57.701 | 74.137 | 20000/20000 |

### p99 Delta (Redis - In-memory)

| Endpoint | In-memory p99 | Redis p99 | Delta |
| --- | ---: | ---: | ---: |
| token-bucket | 80.586 | 85.271 | +4.685 |
| fixed-window | 74.896 | 82.823 | +7.927 |
| sliding-window | 75.653 | 76.378 | +0.725 |
| leaky-bucket | 74.146 | 74.137 | -0.009 |

## Project Structure

```text
src/main/java/com/example/ratelimiter
├── DistributedRateLimiterApplication.java
├── config/
├── controller/
├── filter/
├── model/
├── service/
└── strategy/
    └── algorithm/
```

## Troubleshooting

### Port 8080 already in use

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
kill <PID>
```

### Redis not reachable

```bash
redis-cli ping
brew services start redis
```

### Slow first failure when Redis goes down

Initial fallback can wait up to `ratelimiter.redis.timeout-ms`.

```properties
ratelimiter.redis.timeout-ms=100
```

## Status

- Algorithm endpoints implemented and wired
- Redis + Lua execution implemented
- Circuit-breaker fallback implemented
- Health, readiness, and metrics endpoints implemented
- Benchmark evidence committed for in-memory and Redis modes
- 67 tests passing

