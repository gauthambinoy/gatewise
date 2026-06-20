# Auvex — Architecture

Diagrams here are derived from the real code and kept in sync with it. More views
(request sequence, deployment topology, trust boundaries) are added as those
parts of the system land.

## Entity-Relationship — data model

Source of truth: `gateway/src/main/resources/db/migration/V1__init.sql`.

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
        timestamptz created_at
        timestamptz updated_at
    }
    audit_log {
        bigint id PK
        uuid tenant_id FK
        uuid request_id
        text actor
        text model
        text verdict
        text prompt_redacted
        text response_redacted
        text prev_hash
        text entry_hash UK
        timestamptz created_at
    }
```

**What it shows:** a `tenant` is the unit of isolation; it issues `api_key`s
(stored only as a hash + display prefix), owns allow/deny `policy` rows, and
accumulates an append-only, hash-chained `audit_log`. The audit log keeps only
redacted text — raw PII never reaches it.
