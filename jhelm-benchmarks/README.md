# jhelm-benchmarks

JMH render benchmarks for `jhelm-core`. **Network-free** — renders a bundled
representative chart, so it runs anywhere and can gate perf regressions.

Not published to Maven Central (`maven.deploy.skip` + `central-publishing` skip);
checkstyle/pmd/jacoco are skipped (still spring-javaformat-clean).

## The workload

`src/main/resources/charts/bench-app` is a synthetic-but-representative chart chosen
to exercise the real render hot path rather than a toy:

- **nested values** (`resources`, `autoscaling`, `ingress.hosts[]`, `env[]`, a deep
  `config` map) → values coalescing + `Double`-boxing (#718),
- a **library subchart** (`bench-lib`, `type: library`) with a `define` → named-template
  resolution + the parse-cache path (#726),
- templates leaning on **`include`/`toYaml`/`range`** (`deployment`, `configmap`, `ingress`,
  `hpa`, …) → function dispatch + `toYaml` quoting churn (#720).

## Run

Build the shaded runner, then run the canonical JMH jar:

```bash
./mvnw -pl jhelm-benchmarks -am package -DskipTests
java -jar jhelm-benchmarks/target/benchmarks.jar RenderBenchmark -prof gc
```

Quick loop (1 fork, shorter iterations):

```bash
java -jar jhelm-benchmarks/target/benchmarks.jar RenderBenchmark -f 1 -wi 3 -i 5 -w 1 -r 2 -prof gc
```

For a source-attributed allocation/CPU view, capture JFR and analyse with jvmlens:

```bash
java -jar jhelm-benchmarks/target/benchmarks.jar RenderBenchmark.render -f 1 -wi 3 -i 6 -prof "jfr:dir=/tmp/jfr-before"
jvmlens analyze /tmp/jfr-before -a org.alexmond.jhelm,org.alexmond.gotmpl4j
```

## Benchmarks

- **`render`** — steady state: one warm `Engine` (shared registry + parse cache warm)
  renders the chart repeatedly. **This is the target the render-path tickets move** (#718,
  #719, #720). Pair with `-prof gc` for `B/op`.
- **`renderFreshEngine`** — a new `Engine` per op; the gap vs `render` is the per-engine
  construction cost the shared registry (#717) amortises.
- **`loadChart`** — one-time `ChartLoader.load` (parse of `Chart.yaml`/`values.yaml`/templates).

## Baseline

Indicative numbers (JDK 21, one dev machine — treat as a **relative** baseline; re-measure
on your box before/after a change). Throughput ops/ms, allocation `B/op` from `-prof gc`.

| Benchmark | ops/ms | B/op |
|---|---:|---:|
| `render` (warm) | ~1.14 | ~286,000 |
| `renderFreshEngine` | ~0.73 | ~449,500 |
| `loadChart` | ~0.54 | ~422,000 |

`render` vs `renderFreshEngine` (286 KB vs 450 KB/op, ~1.6×) is the shared-registry win
already banked by #717; `render`'s ~286 KB/op is the number #718/#720 aim to cut.
