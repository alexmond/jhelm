# Test Charts

Test charts used by `EngineTest` and other integration tests.

## Chart Index

### minimal
Basic chart with a single ConfigMap template. Tests simple value substitution.

### chart-with-dependencies
Chart declaring external Bitnami dependencies (postgresql, redis). Tests dependency
management and conditional subchart rendering (`condition: xxx.enabled`).

### hooks-test
Chart with pre-install and post-install hook Jobs. Tests Helm lifecycle hook
annotations (`helm.sh/hook`, `helm.sh/hook-weight`, `helm.sh/hook-delete-policy`).

### variable-scoping-test
Comprehensive chart testing template variable scoping: `range`, `with`, `if`,
`$` root access, and nested scopes. Contains a subchart for cross-chart variable
access. See its own README.md for regression test details.

### subchart-value-propagation
Tests that parent chart templates can access deep subchart default values — the
pattern used by gitea, airflow, and other complex charts. Covers:
- **Direct access**: `index .Values "database" "service" "port"` reads subchart defaults
- **Computed helpers**: `_helpers.tpl` builds connection strings from subchart values
- **Override merging**: parent values override subchart defaults (e.g., `database.auth.password`)
- **Global propagation**: `global.env` is accessible in both parent and subchart contexts
- **Subcharts**: `database` (port 5432) and `cache` (port 6379) mock real dependencies

### reverse-execution-order
Tests Helm 4's reverse-alphabetical template execution order (istiod pattern, issue #200).
`zzz_profile.yaml` merges `_internal_defaults` into `.Values` before `deployment.yaml`
reads `.Values.global.scope`. If execution order is wrong, `.Values.global.scope` is undefined.

### dependency-conditions
Tests dependency condition evaluation and alias propagation using `requirements.yaml`
(datadog pattern, issue #202). Covers:
- **Condition evaluation**: `app.monitoring.enabled=false` skips the monitoring subchart
- **Alias propagation**: `alias: api` renames backend subchart's `.Chart.Name` to `api`
- **requirements.yaml loading**: apiVersion v1 chart with separate requirements file
- **Subcharts**: `backend` (alias `api`, port 8080) and `monitoring` (disabled, port 9090)

### library-chart
Tests library chart pattern (issue #172). A library subchart (`type: library`) provides
named templates via `include` but its standalone `.yaml` templates must not render. Covers:
- **Helper availability**: parent uses `{{ include "mylib.fullname" . }}` from library
- **Template suppression**: library's `should-not-render.yaml` must not appear in output
