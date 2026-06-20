#!/usr/bin/env bash
#
# parity-run.sh — run the FULL chart-parity suite (every chart in charts.csv) and
# print a categorised summary. This is the definitive regression for engine changes
# (it's what the nightly parity CI job runs). Slow + network-bound.
#
# Usage:
#   parity-run.sh                 # run all charts.csv, summarise
#   parity-run.sh --fails-only    # only list the failing charts
#
# Exit 0 iff the whole suite passed.

. "$(dirname "$0")/lib.sh"

fails_only=0; [[ "${1:-}" == "--fails-only" ]] && fails_only=1

total="$(grep -cv '^$' "$CHARTS_CSV")"
echo "${c_dim}running the full parity suite over $total charts (this takes a few minutes)...${c_off}" >&2
log="$(mktemp)"; run_full_parity > "$log"

# The Maven run is quiet (no "Tests run:" line), but the surefire XML always has the
# counts and the per-chart pass/fail is logged by the test itself. Read the XML.
report="$(ls -t "$REPO_ROOT"/jhelm-core/target/surefire-reports/TEST-*KpsComparison*.xml 2>/dev/null | head -1 || true)"
if [[ -n "$report" ]]; then
  attr() { grep -oE "$1=\"[0-9]+\"" "$report" | head -1 | grep -oE '[0-9]+'; }
  echo "Surefire: tests=$(attr tests) failures=$(attr failures) errors=$(attr errors)"
else
  echo "${c_red}no surefire report — the build likely failed before tests ran:${c_off}"
  grep -E '\[ERROR\]' "$log" | grep -vE 'For more information|Re-run|help|See ' | head -8
  rm -f "$log"; exit 1
fi

# Categorised failure list (these lines are slf4j output, present even under -q).
mapfile -t failing < <(grep -Eo '[a-z0-9._/-]+ - ([0-9]+ comparison failure|JHelm rendering failed)' "$log" | sort -u)
if [[ ${#failing[@]} -eq 0 ]]; then
  echo "${c_green}All $total charts pass.${c_off}"; rm -f "$log"; exit 0
fi

echo "${c_red}${#failing[@]} failing:${c_off}"
for f in "${failing[@]}"; do
  name="${f% - *}"
  if [[ "$f" == *"JHelm rendering failed"* ]]; then
    [[ $fails_only -eq 1 ]] && { echo "$name"; continue; }
    printf '  %sRENDERFAIL%s  %s\n' "$c_red" "$c_off" "$name"
  else
    [[ $fails_only -eq 1 ]] && { echo "$name"; continue; }
    diff="$(grep -A2 "$name - [0-9]* comparison failure" "$log" | grep -E ': expected=|missing in JHelm' | head -1 | sed 's/^[[:space:]]*//' | cut -c1-100)"
    printf '  %sCOMPFAIL%s    %s  %s%s%s\n' "$c_red" "$c_off" "$name" "$c_dim" "${diff}" "$c_off"
  fi
done
rm -f "$log"
exit 1
