# Benchmarks

This folder contains a lightweight benchmark harness to generate throughput and latency evidence for the Java rate limiter.

## Prerequisites

- App running locally
- Python 3

## Run Benchmark (Token Bucket)

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
python3 benchmarks/load_benchmark.py \
  --url http://localhost:8080/api/limited/token-bucket \
  --requests 10000 \
  --concurrency 500 \
  --timeout 2 \
  --output benchmarks/results/token-bucket-local.json
```

## Run Benchmark (Other Algorithms)

```bash
cd /Users/rishabh/IdeaProjects/ratelimiter
python3 benchmarks/load_benchmark.py --url http://localhost:8080/api/limited/fixed-window --requests 10000 --concurrency 500 --output benchmarks/results/fixed-window-local.json
python3 benchmarks/load_benchmark.py --url http://localhost:8080/api/limited/sliding-window --requests 10000 --concurrency 500 --output benchmarks/results/sliding-window-local.json
python3 benchmarks/load_benchmark.py --url http://localhost:8080/api/limited/leaky-bucket --requests 10000 --concurrency 500 --output benchmarks/results/leaky-bucket-local.json
```

## Output

The script prints and writes JSON with:

- Throughput (requests/sec)
- Status code distribution
- Latency avg, p50, p95, p99, max (ms)

Use `benchmarks/results-template.md` to record published benchmark claims.

