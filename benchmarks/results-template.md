# Benchmark Results Template

## Environment

- Machine:
- CPU / RAM:
- OS:
- Java version:
- Redis version:
- App config (`ratelimiter.*`):
- Redis mode: local / remote / cluster

## Workload

- Total requests:
- Concurrency:
- Endpoint:
- Warmup run done: yes/no

## Results

| Algorithm | Endpoint | Throughput (req/s) | p95 (ms) | p99 (ms) | Max (ms) | Notes |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Token Bucket | `/api/limited/token-bucket` | | | | | |
| Fixed Window | `/api/limited/fixed-window` | | | | | |
| Sliding Window | `/api/limited/sliding-window` | | | | | |
| Leaky Bucket | `/api/limited/leaky-bucket` | | | | | |

## Failover Check

- Redis stopped during run: yes/no
- Circuit open duration observed:
- First-failure latency spike:
- Recovery behavior observed:

## Published Claim Decision

- Ready to claim `10k+ req/s`: yes/no
- Ready to claim `<1ms p99`: yes/no
- Ready to claim `<500ms failover`: yes/no
- Notes:

