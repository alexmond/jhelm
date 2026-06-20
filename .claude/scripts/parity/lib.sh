#!/usr/bin/env bash
# Shared helpers for the jhelm chart-parity scripts.
# Source this from the other scripts: . "$(dirname "$0")/lib.sh"

set -euo pipefail

# Repo root = two levels up from .claude/scripts/parity
PARITY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$PARITY_DIR/../../.." && pwd)"

CHARTS_CSV="$REPO_ROOT/jhelm-core/src/test/resources/charts.csv"
FAILED_CSV="$REPO_ROOT/jhelm-core/src/test/resources/failed.csv"
SINGLE_CSV="$REPO_ROOT/jhelm-core/src/test/resources/single.csv"
SKIP_CSV="$REPO_ROOT/jhelm-core/src/test/resources/charts-skip.csv"
# The dev placeholder single.csv is restored to after every run.
SINGLE_PLACEHOLDER='dandydev-charts/redis-ha,dandydev-charts,https://dandydeveloper.github.io/charts/'

c_green=$'\033[32m'; c_red=$'\033[31m'; c_yellow=$'\033[33m'; c_dim=$'\033[2m'; c_off=$'\033[0m'

# mvn wrapper that builds jhelm-core + upstream engine modules and runs only the
# untagged single-chart comparison test. Quiet; returns the surefire log on stdout.
run_single_chart_test() {
  ( cd "$REPO_ROOT" && ./mvnw -q -pl jhelm-core -am test \
      -Dtest='KpsComparisonTest#compareSingleChart' \
      -Dsurefire.failIfNoSpecifiedTests=false 2>&1 ) || true
}

# Run the FULL tagged comparison suite over charts.csv (every chart). Slow + networked.
run_full_parity() {
  ( cd "$REPO_ROOT" && ./mvnw -q -pl jhelm-core -am test \
      -Dtest='KpsComparisonTest#compareAllTopCharts' \
      -Dsurefire.excludedGroups= -Dgroups=comparison \
      -Dsurefire.failIfNoSpecifiedTests=false 2>&1 ) || true
}

# Restore single.csv to its dev placeholder. Registered as an EXIT trap by callers.
restore_single_csv() { printf '%s\n' "$SINGLE_PLACEHOLDER" > "$SINGLE_CSV"; }

# Chart names already present in charts.csv (one per line).
existing_chart_names() { cut -d, -f1 "$CHARTS_CSV" | grep -v '^$' | sort -u; }

# Categorise a captured surefire log (stdin) for a given chart name, echoing one of:
# PASS | COMPFAIL | RENDERFAIL | FETCHFAIL | HELMFAIL | UNKNOWN
classify_chart() {
  local chart="$1" log="$2"
  if grep -qF "$chart - All resources match Helm output" "$log"; then echo PASS
  elif grep -qE "$chart - [0-9]+ comparison failure" "$log"; then echo COMPFAIL
  elif grep -qF "$chart - JHelm rendering failed" "$log"; then echo RENDERFAIL
  elif grep -qF "$chart - Helm template failed, skipping" "$log"; then echo HELMFAIL
  elif grep -qiE "Failed to pull chart $chart|No versions found for chart" "$log"; then echo FETCHFAIL
  else echo UNKNOWN; fi
}
