#!/usr/bin/env bash
#
# chart-test.sh — test candidate Helm charts against `helm template` and print a
# categorised summary. This is the core campaign loop: stage charts in single.csv,
# run the single-chart comparison, parse pass/fail, restore single.csv.
#
# Usage:
#   chart-test.sh CHART,REPOID,REPOURL [more rows...]      # rows as args
#   chart-test.sh -f candidates.txt                        # rows from a file
#   cat candidates.txt | chart-test.sh                     # rows from stdin
#
# Each row is `chartName,repoId,repoUrl` (same format as charts.csv). Rows whose
# chartName is already in charts.csv are skipped and reported as DUP.
#
# Output: one line per chart — PASS / COMPFAIL / RENDERFAIL / FETCHFAIL / HELMFAIL —
# with the first diff for comparison failures. Exit 0 iff every non-dup chart PASSed.

. "$(dirname "$0")/lib.sh"

candidates=()
if [[ "${1:-}" == "-f" ]]; then
  [[ -f "${2:-}" ]] || { echo "no such file: ${2:-}" >&2; exit 2; }
  mapfile -t candidates < <(grep -vE '^\s*#|^\s*$' "$2")
elif [[ $# -gt 0 ]]; then
  candidates=("$@")
else
  mapfile -t candidates < <(grep -vE '^\s*#|^\s*$' /dev/stdin)
fi
[[ ${#candidates[@]} -gt 0 ]] || { echo "no candidate rows given" >&2; exit 2; }

# Split into to-test vs already-present duplicates.
mapfile -t have < <(existing_chart_names)
declare -A have_set=(); for h in "${have[@]}"; do have_set["$h"]=1; done
to_test=(); dups=()
for row in "${candidates[@]}"; do
  name="${row%%,*}"
  if [[ -n "${have_set[$name]:-}" ]]; then dups+=("$name"); else to_test+=("$row"); fi
done

if [[ ${#dups[@]} -gt 0 ]]; then
  for d in "${dups[@]}"; do printf '%s  DUP%s  %s (already in charts.csv)\n' "$c_yellow" "$c_off" "$d"; done
fi
[[ ${#to_test[@]} -gt 0 ]] || { echo "nothing new to test"; exit 0; }

trap restore_single_csv EXIT
printf '%s\n' "${to_test[@]}" > "$SINGLE_CSV"

echo "${c_dim}testing ${#to_test[@]} chart(s) via KpsComparisonTest#compareSingleChart...${c_off}" >&2
log="$(mktemp)"; run_single_chart_test > "$log"

fails=0
for row in "${to_test[@]}"; do
  name="${row%%,*}"
  case "$(classify_chart "$name" "$log")" in
    PASS)       printf '%s  PASS%s        %s\n' "$c_green" "$c_off" "$name" ;;
    COMPFAIL)   diff="$(grep -A2 "$name - [0-9]* comparison failure" "$log" | grep -E ': expected=|missing in JHelm|diff\(s\)' | head -1 | sed 's/^[[:space:]]*//')"
                printf '%s  COMPFAIL%s    %s  %s%s%s\n' "$c_red" "$c_off" "$name" "$c_dim" "${diff:-(see log)}" "$c_off"; fails=$((fails+1)) ;;
    RENDERFAIL) err="$(grep "$name - JHelm rendering failed" "$log" | head -1 | grep -oE "template '[^']*'[^>]*" | head -1)"
                printf '%s  RENDERFAIL%s  %s  %s%s%s\n' "$c_red" "$c_off" "$name" "$c_dim" "${err:-render error}" "$c_off"; fails=$((fails+1)) ;;
    HELMFAIL)   printf '%s  HELMFAIL%s    %s  %s(helm template itself fails — charts-skip.csv)%s\n' "$c_yellow" "$c_off" "$name" "$c_dim" "$c_off" ;;
    FETCHFAIL)  printf '%s  FETCHFAIL%s   %s  %s(chart/version not found — check name/repo)%s\n' "$c_yellow" "$c_off" "$name" "$c_dim" "$c_off" ;;
    *)          printf '%s  UNKNOWN%s     %s\n' "$c_yellow" "$c_off" "$name"; fails=$((fails+1)) ;;
  esac
done
rm -f "$log"

echo
echo "${c_dim}Tip: promote PASS charts into charts.csv; send COMPFAIL/RENDERFAIL through chart-diff.sh.${c_off}" >&2
[[ $fails -eq 0 ]]
