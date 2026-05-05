# DistributedRateLimiter (Java / Spring Boot)

High-performance API rate limiter in Java with algorithm-based routing, Redis primary storage for token bucket, and automatic in-memory fallback via circuit breaker.

This repository is the Java parity build of the .NET `DistributedRateLimiter` workflow.

## What Is Implemented

- Four algorithms exposed as separate API endpoints: token bucket, fixed window, sliding window, leaky bucket
- Request filtering via middleware-style `OncePerRequestFilter`
- Standard rate-limit headers on every protected request:
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`
  - `X-RateLimit-Reset`
  - `Retry-After` when blocked (`429`)
- All four algorithms support Redis-first execution using atomic Lua script updates
- Automatic in-memory fallback when Redis fails or times out
- Redis circuit breaker (`OPEN` / `HALF_OPEN` / `CLOSED`)
- Health and metrics endpoints for operations visibility
- Swagger/OpenAPI docs

## API Endpoints

### Health

- `GET /health/live` - app process liveness
- `GET /health/ready` - readiness with Redis + breaker status
- `GET /health` - detailed mode/status summary

### Rate Limiting

- `GET /api/limited/token-bucket`
- `GET /api/limited/fixed-window`
- `GET /api/limited/sliding-window`
- `GET /api/limited/leaky-bucket`

### Metrics

- `GET /api/metrics` - per-client allowed/blocked/total counters

### Swagger

- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`

## Request Pipeline

```
Incoming Request
    -> RateLimitFilter (client key + endpoint algorithm)
    -> Strategy resolve (token/fixed/sliding/leaky)
    -> Token bucket only: Redis Lua (primary)
         -> timeout/error -> circuit breaker -> in-memory fallback
    -> Set X-RateLimit-* headers
    -> 200 or 429
```

## Configuration

`src/main/resources/application.properties`

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

# Uncomment when Redis is enabled
# spring.data.redis.host=localhost
# spring.data.redis.port=6379
```

## Local Setup

### Prerequisites

- Java 21
- Maven Wrapper (included as `./mvnw`)
- Redis 7+ (optional but required for Redis-primary token bucket)

### Start Redis

macOS (Homebrew):

```bash
brew install redis
brew services start redis
redis-cli ping
```

Linux:

```bash
sudo systemctl start redis-server
redis-cli ping
```

Expected ping response:

```text
PONG
```

## Run

Without Redis (in-memory/local mode):

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
./mvnw spring-boot:run
```

With Redis enabled:

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
./mvnw spring-boot:run -Dspring-boot.run.arguments="--ratelimiter.redis.enabled=true --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6379"
```

## Test

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
./mvnw clean test
```

## Quick Verification

```bash
curl -s http://localhost:8080/health/live
curl -s http://localhost:8080/health/ready
curl -s http://localhost:8080/health
curl -i http://localhost:8080/api/limited/token-bucket
curl -s http://localhost:8080/api/metrics
```

Burst test:

```bash
for i in {1..10}; do
  curl -s -o /dev/null -w "request $i -> HTTP %{http_code}\n" http://localhost:8080/api/limited/token-bucket
done
```

## Benchmark Harness

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
python3 benchmarks/load_benchmark.py \
  --url http://localhost:8080/api/limited/token-bucket \
  --requests 10000 \
  --concurrency 500 \
  --output benchmarks/results/token-bucket-local.json
```

- Full benchmark docs: `benchmarks/README.md`
- Results template: `benchmarks/results-template.md`

## Project Structure

```text
src/main/java/com/example/ratelimiter
├── DistributedRateLimiterApplication.java
├── config
│   ├── OpenApiConfig.java
│   └── RateLimiterProperties.java
├── controller
│   ├── HealthController.java
│   ├── MetricsController.java
│   └── RateLimitController.java
├── filter
│   ├── ClientKeyResolver.java
│   └── RateLimitFilter.java
├── model
│   └── RateLimitDecision.java
├── service
│   ├── PluggableRateLimiterService.java
│   ├── RateLimiterMetricsService.java
│   ├── RateLimiterService.java
│   ├── RedisCircuitBreakerService.java
│   └── RedisHealthService.java
└── strategy
    ├── LimiterStrategy.java
    ├── LimiterStrategyType.java
    └── algorithm
        ├── FixedWindowRateLimiter.java
        ├── LeakyBucketRateLimiter.java
        ├── SlidingWindowRateLimiter.java
        └── TokenBucketRateLimiter.java
```

## Strategy Dispatch (Pluggable Design)

`PluggableRateLimiterService` is the strategy dispatcher. It does not implement rate-limit math; it routes each request to the correct strategy implementation.

```text
RateLimitFilter
  -> resolve endpoint to LimiterStrategyType
  -> RateLimiterService.evaluate(key, strategyType)
       -> PluggableRateLimiterService
            -> map LimiterStrategyType -> LimiterStrategy bean
            -> strategy.evaluate(key)
                 -> RateLimitDecision
```

- `LimiterStrategyType` = algorithm selection enum (`TOKEN_BUCKET`, `FIXED_WINDOW`, `SLIDING_WINDOW`, `LEAKY_BUCKET`)
- `LimiterStrategy` = common contract each algorithm implements
- `PluggableRateLimiterService` = runtime router that selects and invokes the strategy

This design keeps API/filter logic stable while allowing algorithm or backend changes independently.

## Notes on Algorithm vs Backend

Algorithms stay the same; storage backend can change.

- Algorithm identity: token bucket / fixed window / sliding window / leaky bucket
- Backend choice: Redis primary (token bucket path) or in-memory fallback

This keeps API behavior stable while allowing resilience and distributed operation.

## Troubleshooting

### Port 8080 already in use

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
kill <PID>
```

Or run on another port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### Redis connection refused

```bash
brew services start redis
redis-cli ping
```

### Delay after Redis shutdown

First failure may wait up to `ratelimiter.redis.timeout-ms` (default 500ms), then circuit breaker opens and fallback becomes fast.

To reduce initial delay:

```properties
ratelimiter.redis.timeout-ms=100
```

## Current Phase

- [x] Algorithm-first structure
- [x] Redis Lua token bucket + in-memory fallback
- [x] Circuit breaker + health/metrics endpoints
- [x] Redis parity for fixed/sliding/leaky with fallback
- [x] Benchmark harness + baseline result JSON files (all 4 endpoints)
- [x] 67 comprehensive unit + integration + concurrency tests

## .NET Baseline vs Java Status

| Capability | .NET Baseline | Java Status |
| --- | --- | --- |
| Four algorithm endpoints | ✅ | ✅ |
| Token bucket on Redis with Lua | ✅ | ✅ |
| Automatic in-memory fallback | ✅ | ✅ |
| Circuit breaker (5s window) | ✅ | ✅ |
| Health endpoints (`/health/live`, `/health/ready`, `/health`) | ✅ | ✅ |
| Per-client metrics endpoint | ✅ | ✅ |
| Redis-backed fixed/sliding/leaky | ✅ | ✅ Redis-first + fallback |
| 50+ comprehensive tests | ✅ 50+ | ✅ 67 (unit + integration + concurrency) |
| Benchmark result files in repo | ✅ | ✅ `benchmarks/results/` |

### Test Coverage (67 tests — all passing)

| Test Class | Tests | Covers |
| --- | --- | --- |
| `FixedWindowRateLimiterTests` | 11 | allow, block, remaining, retryAfter, reset, isolation, clearAll |
| `TokenBucketRateLimiterTests` | 11 | allow, block, remaining, retryAfter, isolation, clearAll, refill delay |
| `LeakyBucketRateLimiterTests` | 11 | allow, block, remaining, retryAfter, isolation, clearAll, drain delay |
| `SlidingWindowRateLimiterTests` | 10 | allow, block, remaining, retryAfter, isolation, clearAll |
| `RedisCircuitBreakerServiceTests` | 7 | CLOSED→OPEN→HALF_OPEN→CLOSED, re-open on probe failure |
| `RateLimiterFilterIntegrationTests` | 3 | filter blocks after limit, health/metrics excluded |
| `RateLimiterAlgorithmEndpointsIntegrationTests` | 4 | all 4 HTTP endpoints route to correct algorithm |
| `ConcurrentRateLimiterTests` | 5 | 50-thread races all 4 algorithms, per-client isolation |
| `AlgorithmRateLimiterTests` | 3 | refill/drain timing |
| `RateLimiterTokenBucketIntegrationTests` | 1 | token bucket via HTTP |
| `DistributedRateLimiterApplicationTests` | 1 | Spring context loads |

### Performance Benchmarks (Java, MacBook, 20000 req x 200 concurrency)

#### In-memory mode (`redis=DISABLED`)

| Endpoint | RPS | p50 ms | p95 ms | p99 ms | Success |
| --- | --- | --- | --- | --- | --- |
| token-bucket | 5,502.13 | 29.644 | 59.544 | 80.586 | 20000/20000 |
| fixed-window | 5,736.00 | 21.978 | 56.881 | 74.896 | 20000/20000 |
| sliding-window | 5,691.29 | 28.200 | 58.381 | 75.653 | 20000/20000 |
| leaky-bucket | 5,779.19 | 22.222 | 56.625 | 74.146 | 20000/20000 |

#### Redis-primary mode (`redis=UP`, `mode=redis-primary`)

| Endpoint | RPS | p50 ms | p95 ms | p99 ms | Success |
| --- | --- | --- | --- | --- | --- |
| token-bucket | 5,310.93 | 35.637 | 65.571 | 85.271 | 20000/20000 |
| fixed-window | 5,342.61 | 26.866 | 62.281 | 82.823 | 20000/20000 |
| sliding-window | 5,267.40 | 25.795 | 59.806 | 76.378 | 20000/20000 |
| leaky-bucket | 5,310.60 | 22.687 | 57.701 | 74.137 | 20000/20000 |

#### Redis vs In-memory delta (p99)

| Endpoint | In-memory p99 ms | Redis p99 ms | Delta |
| --- | --- | --- | --- |
| token-bucket | 80.586 | 85.271 | +4.685 |
| fixed-window | 74.896 | 82.823 | +7.927 |
| sliding-window | 75.653 | 76.378 | +0.725 |
| leaky-bucket | 74.146 | 74.137 | -0.009 |

> Environment: Java 21, Spring Boot 4.0.6, Tomcat, Apple Silicon MacBook.
> Notes: values are local-loopback end-to-end latencies measured by the Python harness. Use a dedicated load generator host and larger runs for publishable SLA claims.

