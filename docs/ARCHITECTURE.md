# Auvex — Architecture

Diagrams are **Mermaid** (they render natively on GitHub) and are derived from the
real code, not invented. Every node maps to a real module, route, table, or
container. If the code changes, the affected diagram changes with it.

Auvex is a drop-in, OpenAI-compatible **AI gateway**: an app points its base URL at
Auvex instead of the provider, and every LLM call then flows through one controlled
passage that **authenticates**, **routes**, **redacts**, **governs**, **logs**, and
forwards it.

---

## 1. System architecture

All components and how they connect.

```mermaid
flowchart LR
    app["Enterprise app / SDK<br/>(OpenAI-compatible client)"]
    subgraph gw["Auvex gateway (Spring Boot, Java 21)"]
      filter["API-key auth filter"]
      pipe["Chat-completions pipeline<br/>route · redact · policy · budget · audit"]
      mgmt["Management & metrics APIs<br/>/v1/policies · /v1/audit · /v1/usage"]
    end
    pg[("Postgres<br/>tenants · keys · policies · audit_log")]
    redis[("Redis<br/>response cache")]
    kafka[["Kafka<br/>async audit (opt-in)"]]
    primary["Primary provider<br/>(OpenRouter, OpenAI-compatible)"]
    fallback["Fallback provider<br/>(failover, opt-in)"]

    app -->|"Bearer api-key"| filter --> pipe
    app --> mgmt
    pipe -->|"redacted prompt"| primary
    pipe -. "on failure/5xx" .-> fallback
    pipe --> redis
    pipe --> pg
    pipe -. "async mode" .-> kafka -.-> pg
    mgmt --> pg
```

**Caption:** the gateway is the single control point; Postgres is the source of
truth, Redis is a best-effort cache, Kafka is an opt-in async path for audit
writes, and the providers are the only external egress — reached only with an
already-redacted prompt.

---

## 2. Request lifecycle (the hot path)

The core `POST /v1/chat/completions` flow, end to end.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as ApiKeyAuthenticationFilter
    participant Ctl as ChatCompletionsController
    participant R as ModelRouter
    participant Red as PromptRedactor
    participant P as PolicyEnforcement
    participant B as BudgetService
    participant A as AuditSink
    participant U as UpstreamProxy
    participant Prov as Provider

    C->>F: POST /v1/chat/completions (Bearer key)
    F->>F: hash key → resolve tenant (else 401)
    F->>Ctl: authenticated request
    Ctl->>R: resolve model alias (else 400)
    Ctl->>Red: redact message content
    Red-->>Ctl: masked prompt + data types
    Ctl->>P: evaluate policy
    alt denied
        P-->>Ctl: deny
        Ctl->>A: record (blocked)
        Ctl-->>C: 403 policy_violation
    else allowed
        Ctl->>B: within budget?
        alt over budget
            B-->>Ctl: no
            Ctl-->>C: 429 rate_limit_exceeded
        else ok
            Ctl->>A: record (allowed / redacted)
            Ctl->>U: forward (cache → fetch/failover, or stream)
            U->>Prov: redacted request
            Prov-->>U: response
            U-->>C: response (status passed through)
        end
    end
```

**Caption:** the provider is only ever reached on the allowed branch, with a
redacted prompt, and every outcome (blocked, over-budget, allowed) is decided
before any upstream work — and recorded.

---

## 3. Entity-Relationship — data model

Source of truth: `gateway/src/main/resources/db/migration/V1__init.sql` (+ V2 adds
the audit anti-fork constraint).

```mermaid
erDiagram
    tenant ||--o{ api_key : "issues"
    tenant ||--o{ policy : "owns"
    tenant ||--o{ audit_log : "records"

    tenant {
        uuid id PK
        text name
        text slug UK
        text status
        timestamptz created_at
        timestamptz updated_at
    }
    api_key {
        uuid id PK
        uuid tenant_id FK
        text name
        text key_hash UK
        text prefix
        text status
        timestamptz last_used_at
        timestamptz expires_at
        timestamptz created_at
    }
    policy {
        uuid id PK
        uuid tenant_id FK
        text name
        text effect
        text resource_type
        text resource_value
        int priority
        boolean enabled
    }
    audit_log {
        bigint id PK
        uuid tenant_id FK
        uuid request_id
        text verdict
        text prompt_redacted
        text prev_hash
        text entry_hash UK
        timestamptz created_at
    }
```

**Caption:** a tenant owns its keys (stored only as a hash + display prefix), its
policies, and an append-only, hash-chained audit log holding redacted text only.

---

## 4. Component / module map

The Java packages and their dependencies (web → domain → data).

```mermaid
flowchart TD
    web["web<br/>controllers · DTOs · error handler"]
    auth["auth<br/>key hash · filter · TenantContext"]
    routing["routing<br/>ModelRouter (allow-list)"]
    redaction["redaction<br/>detectors · Luhn/IBAN · engine"]
    policy["policy<br/>engine · enforcement"]
    budget["budget<br/>BudgetService"]
    audit["audit<br/>hash-chain · sinks · consumer"]
    cache["cache<br/>ResponseCache (Redis)"]
    proxy["proxy<br/>UpstreamProxy (+ failover)"]
    tenant["tenant<br/>entities · repos"]
    config["config<br/>@ConfigurationProperties"]

    web --> auth & routing & redaction & policy & budget & audit & cache & proxy
    auth --> tenant
    policy --> tenant
    budget --> audit
    cache --> proxy
    audit --> tenant
    proxy --> config
    routing --> config
    redaction -. "pure, no deps" .-> redaction
```

**Caption:** the web layer orchestrates the domain modules; redaction is pure
(JDK-only, no Spring); config holds the typed, validated settings each module reads.

---

## 5. Request state machine

The states a request moves through, and where it can terminate.

```mermaid
stateDiagram-v2
    [*] --> Received
    Received --> Unauthorized: missing/invalid key
    Received --> Authenticated: key resolves tenant
    Authenticated --> BadModel: unknown alias
    Authenticated --> Routed: alias resolved
    Routed --> Redacted: prompt masked
    Redacted --> Blocked: policy denies
    Redacted --> OverBudget: budget exceeded
    Redacted --> Allowed: permitted
    Allowed --> Forwarded: provider responds
    Forwarded --> [*]
    Blocked --> [*]
    OverBudget --> [*]
    Unauthorized --> [*]
    BadModel --> [*]

    note right of Blocked: audited (blocked) → 403
    note right of Forwarded: audited (allowed/redacted)
```

**Caption:** every terminal state has a defined HTTP outcome; the two that involve
a real decision (Blocked, Forwarded) are written to the audit log.

---

## 6. Security / trust boundaries

What's untrusted, where it's checked, and what's allowed to cross to the provider.

```mermaid
flowchart LR
    subgraph untrusted["Untrusted"]
      client["Client request<br/>(raw prompt, may contain PII/secrets)"]
    end
    subgraph gw["Trust boundary — Auvex"]
      authb["Authenticate (API key → tenant)"]
      validate["Validate payload shape"]
      redact["Redact PII/secrets"]
      govern["Policy + budget gate"]
      audit["Record (redacted only)"]
    end
    subgraph external["External egress"]
      provider["Model provider"]
    end

    client --> authb --> validate --> redact --> govern --> provider
    govern --> audit
    redact -. "raw PII never crosses" .-> provider
```

**Caption:** untrusted input is authenticated, validated, and **redacted before it
crosses the boundary**; only redacted text is forwarded or stored, and policy +
budget gate the egress.

---

## 7. Deployment — local stack today

What `docker compose up` runs (the real, tested topology). The AWS topology
(VPC, EC2/RDS/S3+CloudFront, TLS, CI→GHCR→VM) is **Phase 5** and will be added
here when it's built as Terraform.

```mermaid
flowchart TD
    subgraph compose["docker compose (one command)"]
      g["gateway container<br/>multi-stage build, non-root JRE"]
      p[("postgres:17")]
      r[("redis:7")]
    end
    dev["Developer / client"] -->|":8080"| g
    g --> p
    g --> r
```

**Caption:** a single command boots gateway + Postgres + Redis, all health-gated;
the gateway image builds the jar from source in-stage and runs as a non-root user.
