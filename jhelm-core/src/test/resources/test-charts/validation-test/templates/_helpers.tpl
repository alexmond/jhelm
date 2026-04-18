{{- define "validate" -}}
{{- $messages := list -}}
{{- $messages := append $messages (include "check.ldap" .) -}}
{{- $messages := append $messages (include "check.watermark" .) -}}
{{- $messages := without $messages "" -}}
{{- $message := join "\n" $messages -}}
{{- if $message -}}
{{-   printf "\nVALUES VALIDATION:\n%s" $message | fail -}}
{{- end -}}
{{- end -}}

{{/* Exact rabbitmq pattern: outer if has NO right-trim marker */}}
{{- define "check.ldap" -}}
{{- if .Values.ldap.enabled }}
{{- if not .Values.ldap.server }}
validation: ldap.server is required when ldap is enabled
{{- end -}}
{{- end -}}
{{- end -}}

{{/* Uses eq comparison */}}
{{- define "check.watermark" -}}
{{- if and (not (eq .Values.watermark.type "absolute")) (not (eq .Values.watermark.type "relative")) }}
validation: watermark.type must be "absolute" or "relative"
{{- end -}}
{{- end -}}
