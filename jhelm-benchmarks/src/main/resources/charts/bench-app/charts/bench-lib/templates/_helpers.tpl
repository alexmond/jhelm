{{- define "bench-lib.annotations" -}}
bench.example.com/generated-by: jhelm-benchmark
bench.example.com/rev: {{ .Release.Revision | quote }}
{{- end -}}
