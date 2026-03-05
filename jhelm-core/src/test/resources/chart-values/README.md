# Chart Values for Comparison Tests

This directory contains per-chart values YAML files used by `KpsComparisonTest`
to provide mandatory or non-default values when rendering charts.

## Convention

- File name: `<short-chart-name>.yaml` (e.g. `wordpress.yaml` for `bitnami/wordpress`)
- Mapped in `application-test.yaml` under `jhelmtest.chart-values`:

```yaml
jhelmtest:
  chart-values:
    "[bitnami/wordpress]": "chart-values/wordpress.yaml"
```

## When to add a values file

Add a values file when a chart **requires** mandatory parameters that have no
sensible default.  Without these values both Helm and JHelm fail to render,
so the chart gets silently skipped.

Providing values allows the comparison test to exercise the chart templates
and detect rendering differences.
