# GateWise raw Kubernetes manifests

**Helm is the recommended way to deploy GateWise** (see `../helm/gatewise`). These raw
manifests are a no-Helm alternative — plain `kubectl apply`-able YAML for the same
topology: gateway, console, ingress, config, a secret template, and an optional
in-cluster Postgres + Redis for a quick non-prod spin-up.

## Files

| File | What it is |
|------|------------|
| `00-namespace.yaml` | `gatewise` namespace |
| `10-configmap.yaml` | Gateway env (non-secret) + console nginx upstream config |
| `20-secret.yaml` | **Secret TEMPLATE** — replace placeholders or manage externally |
| `30-gateway.yaml` | Gateway Deployment + Service (port 8080) |
| `40-console.yaml` | Console Deployment + Service (port 80) |
| `50-ingress.yaml` | Ingress + TLS (cert-manager) |
| `60-postgres.yaml` | Optional Postgres Deployment + Service + PVC |
| `61-redis.yaml` | Optional Redis Deployment + Service |
| `kustomization.yaml` | Ties them together for `kubectl apply -k` |

## Before you apply

1. **Secrets.** Do **not** apply `20-secret.yaml` with its placeholders. Either
   edit it with real values (it uses `stringData`, so plain strings — no base64),
   or, preferably, create the Secret out-of-band and remove `20-secret.yaml` from
   `kustomization.yaml`:

   ```
   kubectl create namespace gatewise
   kubectl -n gatewise create secret generic gatewise-secrets \
     --from-literal=openrouter-api-key='sk-or-...' \
     --from-literal=session-secret="$(openssl rand -hex 32)" \
     --from-literal=postgres-password="$(openssl rand -hex 16)"
   ```

   Keys consumed by the gateway:
   `openrouter-api-key` → `OPENROUTER_API_KEY`,
   `session-secret` → `GATEWISE_SESSION_SECRET`,
   `postgres-password` → `SPRING_DATASOURCE_PASSWORD`.

2. **DNS / TLS.** Replace `gatewise.example.com` in `50-ingress.yaml` (rule and tls)
   and set the `cert-manager.io/cluster-issuer` to your issuer.

3. **Databases (production).** Comment out `60-postgres.yaml` and `61-redis.yaml`
   in `kustomization.yaml` and repoint the gateway at managed services by editing
   `SPRING_DATASOURCE_URL` / `REDIS_HOST` / `REDIS_PORT` in `10-configmap.yaml`.

## Apply

```
kubectl apply -k deploy/k8s
```

or, without kustomize:

```
kubectl apply -f deploy/k8s/
```

## Verify

```
kubectl -n gatewise get pods
kubectl -n gatewise port-forward svc/gatewise-gateway 8081:8080
curl http://127.0.0.1:8081/actuator/health
```

## Notes

- Pods run as non-root with `seccompProfile: RuntimeDefault` and all capabilities
  dropped. The gateway uses a read-only root filesystem with a `/tmp` `emptyDir`.
- These manifests deliberately omit the NetworkPolicy set the Helm chart ships;
  add one (or use Helm) if you need DB/Redis isolation.
- The console nginx upstream is set to the in-cluster `gatewise-gateway` Service via
  the mounted `10-configmap.yaml` config (the image bakes in `gateway:8080`,
  which only resolves under docker-compose).
