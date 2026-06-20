# Chart-parity helper scripts

Reusable scripts that automate the repetitive jhelm ↔ `helm template` parity loop
(driving `charts.csv` toward the 0.1.0 conformance gate). They wrap the same Maven
invocations used by `KpsComparisonTest`, parse the surefire output, and always restore
`single.csv` to its dev placeholder on exit.

| Script | What it does | Cost |
|--------|--------------|------|
| `chart-test.sh` | Test candidate charts; categorised PASS / COMPFAIL / RENDERFAIL / FETCHFAIL / HELMFAIL / DUP. Skips charts already in `charts.csv`. | one render per batch |
| `chart-diff.sh` | Render one chart and print the full jhelm-vs-helm diff (every diff / missing resource). Leaves manifests in `target/test-output/`. | one render |
| `parity-run.sh` | Run the **full** suite over all of `charts.csv` and summarise failures — the definitive regression for engine changes. | minutes (all charts) |
| `lib.sh` | Shared paths + the Maven wrappers + the log classifier. Sourced by the others. | — |

## Examples

```bash
# Test a batch of candidates (rows are chartName,repoId,repoUrl — same as charts.csv)
.claude/scripts/parity/chart-test.sh \
  prometheus-community/prometheus-json-exporter,prometheus-community,https://prometheus-community.github.io/helm-charts \
  dexidp/dex,dexidp,https://charts.dexidp.io

# …or from a file / stdin
.claude/scripts/parity/chart-test.sh -f /tmp/candidates.txt

# Diagnose a failing chart (full diff). Looks the repo up from charts.csv/failed.csv
# when given just a name.
.claude/scripts/parity/chart-diff.sh argo/argo-events

# Full regression after an engine change (what the nightly parity job runs)
.claude/scripts/parity/parity-run.sh            # summary
.claude/scripts/parity/parity-run.sh --fails-only
```

## Workflow these encode

1. `chart-test.sh` a batch → promote `PASS` rows into `charts.csv`.
2. `COMPFAIL`/`RENDERFAIL` → `chart-diff.sh <name>` to see the divergence; fix the engine
   or move the chart to `failed.csv` (real bug) / `charts-skip.csv` (helm itself fails) /
   add a `comparison-ignore` in `application-test.yaml` (random/timestamp content).
3. After any engine change, `parity-run.sh` to confirm no regressions across all charts.

See the `jhelm-300-chart-campaign` memory for the surrounding branch/PR conventions.
