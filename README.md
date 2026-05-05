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
- [x] Benchmark harness + results template
- [ ] SLA proof numbers captured in-repo

## .NET Baseline vs Java Status

The .NET project is the performance/production baseline. This Java repo is the parity build in progress.

| Capability | .NET Baseline | Java Status |
| --- | --- | --- |
| Four algorithm endpoints | Implemented | Implemented |
| Token bucket on Redis with Lua | Implemented | Implemented |
| Automatic in-memory fallback | Implemented | Implemented |
| Circuit breaker (5s window) | Implemented | Implemented |
| Health endpoints (`/health/live`, `/health/ready`, `/health`) | Implemented | Implemented |
| Per-client metrics endpoint | Implemented | Implemented |
| Redis-backed fixed/sliding/leaky | Implemented in baseline architecture | Implemented (Redis-first + fallback) |
| SLA proof (`10k+ req/s`, `<1ms p99`, `99.99% uptime`) | Benchmarked and documented | Not yet benchmarked in this repo |
| 50+ comprehensive tests | Implemented | In progress |

### Important Note on Claims

Use the high-performance SLA numbers (`10k+ req/s`, `<1ms p99`, `99.99% uptime`, `<500ms failover`) for this Java repo only after benchmark and failover evidence is added to this repository.

Run benchmarks from `benchmarks/README.md` and capture evidence using `benchmarks/results-template.md`.

