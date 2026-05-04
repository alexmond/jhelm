#!/usr/bin/env python3
"""
Parallel 500-chart comparison test for jhelm.

Splits charts into batches, runs each in a separate JVM process,
auto-detects required Helm values from error messages, and saves
them for reuse.

Usage:
    python3 .claude/scripts/test_500_charts.py [--workers 5] [--charts /tmp/charts-500.csv]
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
SINGLE_CSV = PROJECT_ROOT / "jhelm-core/src/test/resources/single.csv"
SKIP_CSV = PROJECT_ROOT / "jhelm-core/src/test/resources/charts-skip.csv"
VALUES_CACHE = PROJECT_ROOT / "jhelm-core/src/test/resources/chart-values.json"
MVN = str(PROJECT_ROOT / "mvnw")


def load_skip_list():
    skips = set()
    if SKIP_CSV.exists():
        for line in SKIP_CSV.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                skips.add(line.split(",")[0].strip())
    return skips


def load_values_cache():
    if VALUES_CACHE.exists():
        return json.loads(VALUES_CACHE.read_text())
    return {}


def save_values_cache(cache):
    VALUES_CACHE.write_text(json.dumps(cache, indent=2, sort_keys=True))


def extract_required_value(helm_error):
    """Try to extract what value Helm requires from the error message."""
    patterns = [
        # "execution error: ... required value: .Values.xxx"
        r"\.Values\.(\S+)\s+is required",
        r"required.*\.Values\.(\S+)",
        # "clusterName is required" / "hostname is required"
        r"(\w+)\s+is required",
        # values schema: "must have required property 'xxx'"
        r"must have required property '(\w+)'",
        # "set .Values.xxx"
        r"set \.Values\.(\S+)",
        r"--set\s+(\S+)=",
    ]
    for pattern in patterns:
        m = re.search(pattern, helm_error, re.IGNORECASE)
        if m:
            return m.group(1)
    return None


def generate_value(key):
    """Generate a sensible default for a required value."""
    key_lower = key.lower()
    if "host" in key_lower or "domain" in key_lower or "url" in key_lower:
        return "example.com"
    if "name" in key_lower and "cluster" in key_lower:
        return "test-cluster"
    if "name" in key_lower:
        return "test"
    if "password" in key_lower or "secret" in key_lower or "token" in key_lower:
        return "test-secret-123"
    if "email" in key_lower:
        return "test@example.com"
    if "port" in key_lower:
        return "8080"
    if "replica" in key_lower:
        return "1"
    if "server" in key_lower:
        return "127.0.0.1"
    return "test-value"


def test_chart(chart_line, values_cache, attempt=1):
    """Test a single chart. Returns (chart_name, status, detail)."""
    parts = chart_line.strip().split(",")
    if len(parts) < 3:
        return (chart_line, "SKIP", "invalid CSV line")

    chart_name, repo_id, repo_url = parts[0], parts[1], parts[2]

    # Write single.csv for this chart (use unique temp file to avoid races)
    pid = os.getpid()
    tid = id(chart_line) % 10000
    temp_single = PROJECT_ROOT / f"jhelm-core/src/test/resources/single_{pid}_{tid}.csv"
    temp_single.write_text(chart_line + "\n")

    # Clean per-chart temp dir
    sanitized = chart_name.replace("/", "_")
    temp_chart_dir = PROJECT_ROOT / f"jhelm-core/target/temp-charts/{sanitized}"
    if temp_chart_dir.exists():
        subprocess.run(["rm", "-rf", str(temp_chart_dir)], capture_output=True)

    # Build --set args from cache
    set_args = ""
    if chart_name in values_cache:
        pairs = values_cache[chart_name]
        set_args = " ".join(f"--set {k}={v}" for k, v in pairs.items())

    try:
        # Use the temp single CSV
        cmd = [MVN, "test",
               f"-Dtest=KpsComparisonTest#compareSingleChart",
               f"-Dsingle.csv.override={temp_single}",
               "-pl", "jhelm-core", "-q"]
        proc = subprocess.run(cmd, capture_output=True, text=True,
                              timeout=120, cwd=str(PROJECT_ROOT))
        output = proc.stdout + proc.stderr
    except subprocess.TimeoutExpired:
        return (chart_name, "TIMEOUT", "Test timed out after 120s")
    finally:
        temp_single.unlink(missing_ok=True)

    # Classify result
    if "All resources match" in output:
        return (chart_name, "PASS", "")

    if "OutOfMemory" in output:
        return (chart_name, "OOM", "Java heap space")

    if "Helm template failed" in output or "Helm failed" in output:
        # Extract error for value detection
        helm_err = ""
        for line in output.split("\n"):
            if "Helm failed" in line or "execution error" in line:
                helm_err = line
                break

        if attempt == 1:
            # Try to detect required value
            required = extract_required_value(helm_err)
            if required:
                gen_val = generate_value(required)
                if chart_name not in values_cache:
                    values_cache[chart_name] = {}
                values_cache[chart_name][required] = gen_val
                # Retry with the generated value — but we can't easily pass
                # --set to the test. Log it for now.
                return (chart_name, "HELM_NEEDS_VALUES",
                        f"requires {required}={gen_val}")

        return (chart_name, "HELM_FAIL", helm_err[:200])

    if "rendering failed" in output:
        # Extract root cause
        for line in output.split("\n"):
            if "rendering failed:" in line:
                cause = line.split("rendering failed:")[-1].strip()[:200]
                return (chart_name, "RENDER_FAIL", cause)
        return (chart_name, "RENDER_FAIL", "unknown")

    if "comparison failure" in output:
        for line in output.split("\n"):
            if "comparison failure" in line or "missing" in line:
                return (chart_name, "DIFF_FAIL", line.strip()[:200])
        return (chart_name, "DIFF_FAIL", "unknown")

    if "Failed to fetch" in output or "Failed to pull" in output or "404" in output:
        return (chart_name, "DOWNLOAD_FAIL", "")

    if "charts-skip.csv" in output:
        return (chart_name, "SKIP", "in charts-skip.csv")

    return (chart_name, "UNKNOWN", output[-300:] if output else "no output")


def main():
    parser = argparse.ArgumentParser(description="Parallel 500-chart test")
    parser.add_argument("--workers", type=int, default=5,
                        help="Number of parallel workers")
    parser.add_argument("--charts", default="/tmp/charts-500.csv",
                        help="Path to charts CSV")
    args = parser.parse_args()

    # Load charts
    with open(args.charts) as f:
        charts = [line.strip() for line in f if line.strip()]
    print(f"Loaded {len(charts)} charts")

    # Load skip list and values cache
    skip_list = load_skip_list()
    values_cache = load_values_cache()

    # Filter out skipped charts
    active = []
    skipped = []
    for c in charts:
        name = c.split(",")[0]
        if name in skip_list:
            skipped.append(name)
        else:
            active.append(c)
    print(f"Active: {len(active)}, Skipped: {len(skipped)}")

    # Results
    results = {"PASS": [], "RENDER_FAIL": [], "DIFF_FAIL": [],
               "HELM_FAIL": [], "HELM_NEEDS_VALUES": [],
               "DOWNLOAD_FAIL": [], "OOM": [], "TIMEOUT": [],
               "SKIP": skipped, "UNKNOWN": []}

    # Run in parallel
    start = time.time()
    completed = 0

    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(test_chart, c, values_cache): c
                   for c in active}

        for future in as_completed(futures):
            chart_name, status, detail = future.result()
            results[status].append(chart_name)
            completed += 1

            # Progress
            elapsed = time.time() - start
            rate = completed / elapsed if elapsed > 0 else 0
            remaining = (len(active) - completed) / rate if rate > 0 else 0

            symbol = {"PASS": "✓", "RENDER_FAIL": "✗", "DIFF_FAIL": "≠",
                      "HELM_FAIL": "H", "HELM_NEEDS_VALUES": "V",
                      "DOWNLOAD_FAIL": "↓", "OOM": "M", "TIMEOUT": "T",
                      "UNKNOWN": "?"}.get(status, "?")
            detail_str = f" ({detail})" if detail else ""
            print(f"[{completed}/{len(active)}] {symbol} {chart_name}"
                  f"{detail_str}  "
                  f"[{int(remaining)}s remaining]",
                  flush=True)

    # Save values cache
    save_values_cache(values_cache)

    # Print summary
    elapsed = time.time() - start
    print(f"\n{'='*60}")
    print(f"RESULTS ({int(elapsed)}s elapsed, {args.workers} workers)")
    print(f"{'='*60}")
    for status in ["PASS", "RENDER_FAIL", "DIFF_FAIL", "HELM_FAIL",
                    "HELM_NEEDS_VALUES", "DOWNLOAD_FAIL", "OOM",
                    "TIMEOUT", "SKIP", "UNKNOWN"]:
        count = len(results[status])
        if count > 0:
            print(f"  {status:20s}: {count}")

    # Print failures with details
    for status in ["RENDER_FAIL", "DIFF_FAIL", "OOM"]:
        if results[status]:
            print(f"\n--- {status} ---")
            for name in sorted(results[status]):
                print(f"  {name}")

    if results["HELM_NEEDS_VALUES"]:
        print(f"\n--- CHARTS NEEDING VALUES (saved to chart-values.json) ---")
        for name in sorted(results["HELM_NEEDS_VALUES"]):
            vals = values_cache.get(name, {})
            print(f"  {name}: {vals}")

    # Exit code
    real_failures = len(results["RENDER_FAIL"]) + len(results["DIFF_FAIL"])
    print(f"\nReal jhelm failures: {real_failures}")
    return 0 if real_failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
