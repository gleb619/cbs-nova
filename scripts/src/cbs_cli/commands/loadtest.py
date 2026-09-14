import argparse
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Tuple

from ..config import Config
from ..docker import Docker
from ..printer import Printer
from ..runner import Runner


class LoadtestCommand:
    ENDPOINTS = [
        "/api/v1/dsl/definitions",
        "/api/v1/dsl/helpers",
        "/api/v1/dsl/executions?limit=20",
        "/api/v1/dsl/drafts",
    ]

    def run(self, args: argparse.Namespace) -> int:
        Printer.header("Load-testing read-only BFF endpoints...")

        duration = int(os.environ.get("DURATION", "30"))
        concurrency = int(os.environ.get("CONCURRENCY", "10"))
        rps = int(os.environ.get("RPS", "50"))
        max_p99_ms = int(os.environ.get("MAX_P99_MS", "2000"))
        max_error_rate = int(os.environ.get("MAX_ERROR_RATE", "5"))

        if not Docker.is_stack_running():
            Printer.skip(
                "Docker compose stack not running — skipping load test (run: make up && make backend && make frontend)"
            )
            return 0

        endpoints = os.environ.get("LOADTEST_ENDPOINTS", " ".join(self.ENDPOINTS)).split()
        print(
            f"      config:  duration={duration}s  concurrency={concurrency}  "
            f"rps={rps}  max_p99={max_p99_ms}ms  max_error_rate={max_error_rate}%"
        )

        rows: List[Tuple[str, int, int, int, int, int, int]] = []
        for ep in endpoints:
            print(f"  -> {Config.BFF_BASE_URL}{ep}")
            samples = self._collect(Config.BFF_BASE_URL + ep, duration, concurrency, rps)
            total = len(samples)
            if total:
                errors = sum(1 for code, _ in samples if code < 200 or code >= 300)
                err_pct = int(errors * 100 / total)
                actual_rps = int(total / duration) if duration else 0
                times = sorted(t for _, t in samples)
                p50, p95, p99 = self._percentiles(times, 50, 95, 99)
            else:
                errors = 0
                err_pct = 0
                actual_rps = 0
                p50 = p95 = p99 = 0
            rows.append((ep, actual_rps, p50, p95, p99, err_pct, total))

        self._print_table(rows)

        fails = 0
        for ep, _, _, _, p99, err, _ in rows:
            reasons = []
            if p99 > max_p99_ms:
                reasons.append(f"p99={p99}ms exceeds MAX_P99_MS={max_p99_ms}ms")
            if err > max_error_rate:
                reasons.append(f"err={err}% exceeds MAX_ERROR_RATE={max_error_rate}%")
            if reasons:
                fails += 1
                Printer.fail(f"{ep} — {'; '.join(reasons)}")

        if fails:
            print(f"\n{fails} endpoint(s) failed load-test thresholds.")
            return 1
        print("\nAll endpoints within thresholds.")
        return 0

    def _collect(
        self, url: str, duration: int, concurrency: int, rps: int
    ) -> List[Tuple[int, float]]:
        end = time.time() + duration
        samples: List[Tuple[int, float]] = []
        lock = threading.Lock()
        interval = concurrency / rps if rps > 0 else 0

        def one_request() -> None:
            result = Runner.run(
                [
                    "curl",
                    "--silent",
                    "--show-error",
                    "--max-time",
                    "15",
                    "-o",
                    "/dev/null",
                    "-w",
                    "%{http_code} %{time_total}",
                    url,
                ],
                capture=True,
                check=False,
            )
            try:
                parts = result.stdout.strip().split()
                code = int(parts[0]) if parts else 0
                elapsed = float(parts[1]) if len(parts) > 1 else 0.0
            except (ValueError, IndexError):
                code = 0
                elapsed = 0.0
            with lock:
                samples.append((code, elapsed))

        while time.time() < end:
            with ThreadPoolExecutor(max_workers=concurrency) as pool:
                futures = [pool.submit(one_request) for _ in range(concurrency)]
                for _ in as_completed(futures):
                    pass
            if interval > 0:
                time.sleep(interval)
        return samples

    @staticmethod
    def _percentiles(sorted_times: List[float], *ps: int) -> Tuple[int, ...]:
        total = len(sorted_times)
        if total == 0:
            return tuple(0 for _ in ps)
        result = []
        for p in ps:
            idx = int(total * p / 100)
            idx = max(0, min(idx, total - 1))
            result.append(int(sorted_times[idx] * 1000 + 0.5))
        return tuple(result)

    @staticmethod
    def _print_table(rows: List[Tuple[str, int, int, int, int, int, int]]) -> None:
        print()
        print(
            f"{'endpoint':<40} | {'rps':>6} | {'p50':>6} | {'p95':>6} | "
            f"{'p99':>6} | {'err%':>7} | {'total':>7}"
        )
        print(
            "-" * 40 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 8 + "+" +
            "-" * 8 + "+" + "-" * 9 + "+" + "-" * 9
        )
        for row in sorted(rows, key=lambda r: r[4], reverse=True):
            ep, rps, p50, p95, p99, err, total = row
            print(
                f"{ep:<40} | {rps:>6} | {p50:>6} | {p95:>6} | "
                f"{p99:>6} | {err:>7} | {total:>7}"
            )
