#!/usr/bin/env bash
# Bounded build helper for the jhelm parity workflow.
#
# Wraps the two Maven goals run constantly while iterating on engine fixes —
# spring-javaformat:apply (auto-format) then a build — into a single allowlisted
# entry point, so the loop does not trip a permission prompt on every raw mvnw call.
#
# Usage:
#   build.sh [-v] [-t] [module ...]
#     module ...  Maven module(s) to act on (default: jhelm-gotemplate
#                 jhelm-gotemplate-sprig jhelm-gotemplate-helm jhelm-core).
#     -v          Run `validate` (checkstyle/PMD/format gates) instead of install.
#     -t          Run tests (default skips them: -DskipTests).
#
# Goals are fixed (format:apply, then install or validate); only the module list
# and the two flags vary, so this stays bounded rather than an arbitrary mvn shell.
set -euo pipefail

PARITY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$PARITY_DIR/../../.." && pwd)"

goal="install"
skip_tests="-DskipTests"
modules=()
while [ $# -gt 0 ]; do
  case "$1" in
    -v) goal="validate" ;;
    -t) skip_tests="" ;;
    -*) echo "build.sh: unknown flag '$1'" >&2; exit 2 ;;
    *)  modules+=("$1") ;;
  esac
  shift
done

if [ ${#modules[@]} -eq 0 ]; then
  modules=(jhelm-gotemplate jhelm-gotemplate-sprig jhelm-gotemplate-helm jhelm-core)
fi
pl=$(IFS=,; echo "${modules[*]}")

cd "$REPO_ROOT"
echo "[build.sh] spring-javaformat:apply -pl $pl"
./mvnw -q spring-javaformat:apply -pl "$pl"
# When tests are skipped there is no coverage data, so the jacoco check goal would
# fail the build — skip it too (it runs in full CI / when -t is passed).
jacoco_skip=""
[ -n "$skip_tests" ] && jacoco_skip="-Djacoco.skip=true"
echo "[build.sh] $goal -pl $pl ${skip_tests:-(with tests)}"
./mvnw -q "$goal" -pl "$pl" $skip_tests $jacoco_skip
