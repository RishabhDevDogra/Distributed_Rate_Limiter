#!/usr/bin/env python3
import argparse
import json
import math
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def percentile(sorted_values, p):
    if not sorted_values:
        return 0.0
    if p <= 0:
        return float(sorted_values[0])
    if p >= 100:
        return float(sorted_values[-1])
    k = (len(sorted_values) - 1) * (p / 100.0)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return float(sorted_values[int(k)])
    d0 = sorted_values[f] * (c - k)
    d1 = sorted_values[c] * (k - f)
    return float(d0 + d1)


def request_once(url, timeout):
    start = time.perf_counter()
    code = 0
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            code = resp.getcode()
            _ = resp.read(64)
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception:
        code = 0
    elapsed_ms = (time.perf_counter() - start) * 1000.0
    return code, elapsed_ms


def run_benchmark(url, total_requests, concurrency, timeout):
    latencies = []
    status_counts = {}

    begin = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(request_once, url, timeout) for _ in range(total_requests)]
        for f in as_completed(futures):
            code, elapsed = f.result()
            latencies.append(elapsed)
            status_counts[str(code)] = status_counts.get(str(code), 0) + 1
    duration = max(time.perf_counter() - begin, 1e-9)

    latencies.sort()
    success = sum(v for k, v in status_counts.items() if k.startswith("2"))

    return {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "url": url,
        "requests": total_requests,
        "concurrency": concurrency,
        "duration_seconds": round(duration, 4),
        "throughput_rps": round(total_requests / duration, 2),
        "success_count": success,
        "status_counts": status_counts,
        "latency_ms": {
            "avg": round(statistics.mean(latencies), 3) if latencies else 0.0,
            "p50": round(percentile(latencies, 50), 3),
            "p95": round(percentile(latencies, 95), 3),
            "p99": round(percentile(latencies, 99), 3),
            "max": round(max(latencies), 3) if latencies else 0.0,
        },
    }


def main():
    parser = argparse.ArgumentParser(description="Simple HTTP benchmark harness for DistributedRateLimiter")
    parser.add_argument("--url", required=True, help="Endpoint URL to benchmark")
    parser.add_argument("--requests", type=int, default=10000, help="Total requests")
    parser.add_argument("--concurrency", type=int, default=200, help="Concurrent workers")
    parser.add_argument("--timeout", type=float, default=2.0, help="Request timeout in seconds")
    parser.add_argument("--output", default="benchmarks/results/latest.json", help="JSON result path")
    args = parser.parse_args()

    result = run_benchmark(args.url, args.requests, args.concurrency, args.timeout)

    print(json.dumps(result, indent=2))

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2)


if __name__ == "__main__":
    main()

