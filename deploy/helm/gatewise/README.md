# GateWise Helm chart

Deploys the GateWise AI governance gateway (Spring Boot) and its React admin console
(nginx), with optional bundled PostgreSQL and Redis.

```
gateway   Spring Boot service, listens on 8080, health at /actuator/health
console   nginx serving the SPA, listens on 80, proxies /v1 and /auth to gateway
postgres  optional bundled Bitnami subchart (or point at a managed DB)
redis     optional bundled Bitnami subchart (or point at a managed cache)
```

## Prerequisites

- Kubernetes >= 1.23 (the chart uses `autoscaling/v2` and `networking.k8s.io/v1`)
- Helm 3.8+
- An ingress controller (defaults assume `ingress-nginx`)
- cert-manager with a `ClusterIssuer` (for the default TLS annotation), if you
  keep ingress + TLS enabled
- A CNI that enforces `NetworkPolicy` (Calico, Cilium, …) for the bundled policies
- Fetch subchart dependencies once before installing:

  ```
  helm dependency update deploy/helm/gatewise
  ```

## Quickstart — bundled databases (non-prod / demo)

Brings up gateway + console + an in-cluster Postgres and Redis. You still must
supply the three secrets.

```
helm dependency update deploy/helm/gatewise

helm install gatewise deploy/helm/gatewise \
  --namespace gatewise --create-namespace \
  --set ingress.hosts[0].host=gatewise.your-domain.com \
  --set ingress.tls[0].hosts[0]=gatewise.your-domain.com \
  --set secrets.openrouterApiKey="sk-or-..." \
  --set secrets.sessionSecret="$(openssl rand -hex 32)" \
  --set secrets.postgresPassword="$(openssl rand -hex 16)"
```

> When `postgresql.enabled=true`, also make the bundled Postgres use the same
> password. Either pass it to the subchart explicitly
> (`--set postgresql.auth.password=<same-value>`) or point the subchart at the
> chart Secret (`--set postgresql.auth.existingSecret=gatewise`
> `--set postgresql.auth.secretKeys.userPasswordKey=postgres-password`
> `--set postgresql.auth.secretKeys.adminPasswordKey=postgres-password`).
> The gateway always reads its DB password from `secrets.postgresPassword`.

## Quickstart — external (managed) databases (production)

Disable the bundled DBs and point the gateway at your managed Postgres/Redis.

```
helm install gatewise deploy/helm/gatewise \
  --namespace gatewise --create-namespace \
  --set postgresql.enabled=false \
  --set redis.enabled=false \
  --set externalDatabase.jdbcUrl="jdbc:postgresql://my-rds.example.com:5432/gatewise" \
  --set externalDatabase.username=gatewise \
  --set externalRedis.host=my-elasticache.example.com \
  --set externalRedis.port=6379 \
  --set ingress.hosts[0].host=gatewise.your-domain.com \
  --set ingress.tls[0].hosts[0]=gatewise.your-domain.com \
  --set secrets.existingSecret=gatewise-prod-secrets
```

`externalDatabase.jdbcUrl` takes precedence; if you leave it empty the chart
builds the URL from `externalDatabase.host/port/database`.

## Providing secrets safely

Three secret values are required and are **empty by default** on purpose — the
install fails fast with a clear message if a chart-managed Secret is requested
without them. Never put real values in `values.yaml`.

| Value                        | App env var              | Notes                                  |
|------------------------------|--------------------------|----------------------------------------|
| `secrets.openrouterApiKey`   | `OPENROUTER_API_KEY`     | Upstream LLM provider key              |
| `secrets.sessionSecret`      | `GATEWISE_SESSION_SECRET`   | Console session signing (`openssl rand -hex 32`) |
| `secrets.postgresPassword`   | `SPRING_DATASOURCE_PASSWORD` | DB password                        |

Two ways to supply them:

1. **Chart-managed Secret** (simple): pass the three values via `--set` (or a
   values file kept out of version control / sourced from CI). The chart renders
   a Secret named after the release.
2. **External Secret** (recommended for production): create a Secret out-of-band
   (Sealed Secrets, External Secrets Operator, Vault, a cloud secret manager) and
   set `secrets.existingSecret=<name>`. The chart then renders **no** Secret and
   reads these keys from yours:
   `openrouter-api-key`, `session-secret`, `postgres-password`
   (rename via `secrets.keys.*` if your Secret uses different keys).

## Key values

| Key | Default | Description |
|-----|---------|-------------|
| `gateway.image.repository` | `ghcr.io/gauthambinoy/gatewise-gateway` | Gateway image |
| `gateway.image.tag` | `""` → `appVersion` | Pin in production |
| `gateway.replicaCount` | `2` | Ignored when autoscaling is on |
| `gateway.resources` | 250m/768Mi → 1/1536Mi | JVM workload; tune for your load |
| `gateway.autoscaling.enabled` | `false` | HPA on CPU + memory |
| `gateway.containerPort` | `8080` | Spring `server.port` |
| `console.image.repository` | `ghcr.io/gauthambinoy/gatewise-console` | Console image |
| `console.containerPort` | `80` | nginx listen port |
| `console.overrideNginxConf` | `true` | Render nginx config pointing at the real gateway Service (image bakes in `gateway:8080`) |
| `console.autoscaling.enabled` | `false` | HPA on CPU + memory |
| `serviceAccount.create` | `true` | Token not mounted (no API calls) |
| `ingress.enabled` | `true` | Single ingress; `/`→console (which proxies the API) |
| `ingress.className` | `nginx` | IngressClass |
| `ingress.hosts[0].host` | `gatewise.example.com` | **Replace** |
| `ingress.hosts[0].paths[].service` | `console` | `console` or `gateway` to choose the backend |
| `ingress.tls` | `gatewise-tls` / `gatewise.example.com` | cert-manager-issued |
| `networkPolicy.enabled` | `true` | DB/Redis reachable only from the gateway |
| `networkPolicy.ingressControllerNamespaceSelector` | `ingress-nginx` | Match your controller's namespace label |
| `config.*` | see `values.yaml` | Non-secret env (base URL, model, feature flags) |
| `postgresql.enabled` | `true` | Bundled Bitnami Postgres |
| `redis.enabled` | `true` | Bundled Bitnami Redis (cache) |
| `externalDatabase.*` | empty | Used when `postgresql.enabled=false` |
| `externalRedis.*` | empty | Used when `redis.enabled=false` |
| `secrets.existingSecret` | `""` | Use your own Secret instead of the chart's |

See `values.yaml` for the full, commented set.

## How requests flow

The browser hits a single origin (the console). nginx in the console serves the
SPA and reverse-proxies `/v1` and `/auth` to the gateway Service, so there is no
CORS. The default ingress therefore routes everything to the console; you only
need a `gateway`-backed ingress path if you want to expose the API directly.

## Networking / security notes

- Pods run as non-root with `seccompProfile: RuntimeDefault` and all Linux
  capabilities dropped. The gateway runs with a read-only root filesystem (a
  `/tmp` `emptyDir` is mounted for the JVM); nginx keeps a writable
  cache/run via `emptyDir`s.
- The bundled `NetworkPolicy` set makes Postgres and Redis reachable **only**
  from the gateway, and never externally. The gateway keeps general egress (port
  80/443) so it can reach the upstream LLM provider — narrow this to known CIDRs
  if your provider's IPs are stable.

## Uninstall

```
helm uninstall gatewise --namespace gatewise
```

Bundled Postgres PVCs are retained by default; delete them manually if you want
the data gone.
