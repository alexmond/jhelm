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
