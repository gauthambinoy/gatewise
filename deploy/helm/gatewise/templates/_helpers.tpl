{{/*
Expand the name of the chart.
*/}}
{{- define "gatewise.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name. Truncated at 63 chars for the DNS-name limit on some
Kubernetes resources/labels.
*/}}
{{- define "gatewise.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart name and version, for the helm.sh/chart label.
*/}}
{{- define "gatewise.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every object.
*/}}
{{- define "gatewise.labels" -}}
helm.sh/chart: {{ include "gatewise.chart" . }}
{{ include "gatewise.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: gatewise
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Base selector labels (release-wide; component is added per-workload).
*/}}
{{- define "gatewise.selectorLabels" -}}
app.kubernetes.io/name: {{ include "gatewise.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Per-component labels. Pass a dict: {{ include "gatewise.componentLabels" (dict "ctx" . "component" "gateway") }}
*/}}
{{- define "gatewise.componentLabels" -}}
{{ include "gatewise.labels" .ctx }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Per-component selector labels.
*/}}
{{- define "gatewise.componentSelectorLabels" -}}
{{ include "gatewise.selectorLabels" .ctx }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Component-scoped resource names.
*/}}
{{- define "gatewise.gateway.fullname" -}}
{{- printf "%s-gateway" (include "gatewise.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "gatewise.console.fullname" -}}
{{- printf "%s-console" (include "gatewise.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
ServiceAccount name to use.
*/}}
{{- define "gatewise.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "gatewise.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Name of the Secret the gateway should read credentials from: either the
user-provided existing Secret or the chart-managed one.
*/}}
{{- define "gatewise.secretName" -}}
{{- if .Values.secrets.existingSecret }}
{{- .Values.secrets.existingSecret }}
{{- else }}
{{- include "gatewise.fullname" . }}
{{- end }}
{{- end }}

{{/*
Resolve the gateway image, defaulting the tag to the chart appVersion.
*/}}
{{- define "gatewise.gateway.image" -}}
{{- $tag := .Values.gateway.image.tag | default .Chart.AppVersion -}}
{{- printf "%s:%s" .Values.gateway.image.repository $tag }}
{{- end }}

{{/*
Resolve the console image, defaulting the tag to the chart appVersion.
*/}}
{{- define "gatewise.console.image" -}}
{{- $tag := .Values.console.image.tag | default .Chart.AppVersion -}}
{{- printf "%s:%s" .Values.console.image.repository $tag }}
{{- end }}

{{/*
The DNS name of the gateway Service, used by the console nginx upstream.
*/}}
{{- define "gatewise.gateway.serviceHost" -}}
{{- include "gatewise.gateway.fullname" . }}
{{- end }}

{{/*
JDBC URL for the gateway datasource. Prefers, in order:
  1. an explicit externalDatabase.jdbcUrl
  2. the bundled Postgres subchart service (when postgresql.enabled)
  3. externalDatabase host/port/database
*/}}
{{- define "gatewise.datasourceUrl" -}}
{{- if .Values.externalDatabase.jdbcUrl -}}
{{- .Values.externalDatabase.jdbcUrl -}}
{{- else if .Values.postgresql.enabled -}}
{{- printf "jdbc:postgresql://%s-postgresql:5432/%s" .Release.Name .Values.postgresql.auth.database -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:%v/%s" .Values.externalDatabase.host .Values.externalDatabase.port .Values.externalDatabase.database -}}
{{- end -}}
{{- end }}

{{/*
Datasource username (bundled subchart username, else external username).
*/}}
{{- define "gatewise.datasourceUsername" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.username -}}
{{- else -}}
{{- .Values.externalDatabase.username -}}
{{- end -}}
{{- end }}

{{/*
Redis host. Bundled subchart master service, else external host.
*/}}
{{- define "gatewise.redisHost" -}}
{{- if .Values.redis.enabled -}}
{{- printf "%s-redis-master" .Release.Name -}}
{{- else -}}
{{- .Values.externalRedis.host -}}
{{- end -}}
{{- end }}

{{/*
Redis port.
*/}}
{{- define "gatewise.redisPort" -}}
{{- if .Values.redis.enabled -}}
6379
{{- else -}}
{{- .Values.externalRedis.port -}}
{{- end -}}
{{- end }}
