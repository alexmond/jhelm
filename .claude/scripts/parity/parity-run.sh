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

summary="$(grep -E 'Tests run: [0-9]+, Failures' "$log" | tail -1)"
echo "${summary:-(no surefire summary — build may have failed; see below)}"

# Categorised failure list.
mapfile -t failing < <(grep -Eo '[a-z0-9._/-]+ - ([0-9]+ comparison failure|JHelm rendering failed)' "$log" | sort -u)
if [[ ${#failing[@]} -eq 0 ]]; then
  if grep -q 'BUILD FAILURE' "$log" && [[ -z "$summary" ]]; then
    echo "${c_red}build failed before tests ran:${c_off}"; grep -E '\[ERROR\]' "$log" | grep -vE 'For more information|Re-run|help' | head -8
    rm -f "$log"; exit 1
  fi
  echo "${c_green}All charts pass.${c_off}"; rm -f "$log"; exit 0
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
