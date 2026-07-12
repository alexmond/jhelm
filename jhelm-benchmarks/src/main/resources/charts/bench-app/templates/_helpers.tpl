{{- define "bench-app.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "bench-app.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "bench-app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" -}}
{{- end -}}

{{- define "bench-app.labels" -}}
helm.sh/chart: {{ include "bench-app.chart" . }}
app.kubernetes.io/name: {{ include "bench-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "bench-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "bench-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
