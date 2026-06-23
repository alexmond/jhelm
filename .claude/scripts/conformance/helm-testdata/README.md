# Vendored Helm test source

`funcs_test.go` is vendored verbatim from [helm/helm](https://github.com/helm/helm)
(`pkg/engine/funcs_test.go`). It is input to `../runtv_extract.go -mode helm`, which extracts
the `{tpl, expect, vars}` tables — the `expect` values are Helm's ground-truth outputs taken
verbatim — into the conformance fixture
`jhelm-gotemplate-helm/src/test/resources/conformance/helm_funcs_cases.tsv`.

The committed TSV fixture, not this file, is what `HelmConformanceTest` asserts; this exists
so the fixture can be regenerated from a clean checkout and to pin the upstream revision.

Helm is licensed under the Apache License 2.0 (the same license as this repository).
