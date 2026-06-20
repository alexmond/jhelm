#!/usr/bin/env bash
#
# chart-diff.sh — render one chart and show the full jhelm-vs-helm comparison detail
# (every diff / missing resource), for diagnosing a COMPFAIL/RENDERFAIL chart.
#
# Usage:
#   chart-diff.sh CHART,REPOID,REPOURL
#   chart-diff.sh chartName        # if the chart is already in charts.csv/failed.csv
#
# After a run the rendered manifests are left on disk for inspection:
#   jhelm-core/target/test-output/actual_<chart>.yaml     (jhelm)
#   jhelm-core/target/test-output/expected_<chart>.yaml   (helm)

. "$(dirname "$0")/lib.sh"

arg="${1:-}"; [[ -n "$arg" ]] || { echo "usage: chart-diff.sh CHART[,REPOID,REPOURL]" >&2; exit 2; }

if [[ "$arg" == *,* ]]; then
  row="$arg"
else
  # Look the chart up in charts.csv / failed.csv to recover repoId + url.
  row="$(grep -hE "^$arg," "$CHARTS_CSV" "$FAILED_CSV" 2>/dev/null | head -1 || true)"
  [[ -n "$row" ]] || { echo "chart '$arg' not found in charts.csv/failed.csv — pass the full CHART,REPOID,REPOURL row" >&2; exit 2; }
fi
name="${row%%,*}"

trap restore_single_csv EXIT
printf '%s\n' "$row" > "$SINGLE_CSV"

echo "${c_dim}rendering $name (jhelm + helm)...${c_off}" >&2
log="$(mktemp)"; run_single_chart_test > "$log"

case "$(classify_chart "$name" "$log")" in
  PASS) echo "${c_green}$name — All resources match Helm output.${c_off}" ;;
  RENDERFAIL)
    echo "${c_red}$name — jhelm render error:${c_off}"
    grep "$name - JHelm rendering failed" "$log" | head -1 | grep -oE "chart '[^>]*" | head -1 ;;
  FETCHFAIL) echo "${c_yellow}$name — could not fetch (chart/version not found).${c_off}" ;;
  HELMFAIL)  echo "${c_yellow}$name — helm template itself fails with default values.${c_off}" ;;
  *)
    echo "${c_red}$name — comparison failure(s):${c_off}"
    # Print the diff block: from the failure header to the next blank/failed.csv line.
    awk -v c="$name" '
      $0 ~ c" - [0-9]+ comparison failure" {p=1}
      p {print}
      p && /failed.csv:/ {exit}
    ' "$log" | sed -E 's/^\[[0-9].*KpsComparisonTest.*//; /^\s*$/d' | head -60 ;;
esac
rm -f "$log"
echo
echo "${c_dim}Manifests: target/test-output/actual_*${name//\//_}*.yaml (jhelm) vs expected_* (helm)${c_off}" >&2
