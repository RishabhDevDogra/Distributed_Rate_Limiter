#!/usr/bin/env bash
set -e
BASE="http://localhost:8080/api/limited"
RESULTS="benchmarks/results"

for algo in token-bucket fixed-window sliding-window leaky-bucket; do
  echo "=== $algo ==="
  python3 benchmarks/load_benchmark.py \
    --url "$BASE/$algo" \
    --requests 1000 --concurrency 50 --timeout 2 \
    --output "$RESULTS/$algo-local.json"
  sleep 2
done

echo "=== ALL DONE ==="

