{{/*
Construct database connection string from subchart values.
Mimics gitea pattern: parent template reads deep subchart defaults.
*/}}
{{- define "app.database.host" -}}
{{- printf "%s-database.%s.svc.cluster.local:%v" .Release.Name .Release.Namespace (index .Values "database" "service" "port") -}}
{{- end -}}

{{/*
Construct cache connection string from subchart values.
*/}}
{{- define "app.cache.host" -}}
{{- printf "%s-cache.%s.svc.cluster.local:%v" .Release.Name .Release.Namespace (index .Values "cache" "service" "port") -}}
{{- end -}}
