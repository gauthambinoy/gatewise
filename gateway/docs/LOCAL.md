# Local-first single-binary mode (SQLite)

Auvex normally runs against Postgres + Redis (+ optional Kafka). For an individual developer — a
"vibe coder" who just wants the gateway in front of their own LLM calls — that's heavy. The `sqlite`
profile runs the **entire gateway from one jar against an embedded SQLite file**, with no external
services to stand up.

## Run it

```bash
./gradlew :gateway:bootJar
java -jar gateway/build/libs/gateway.jar --spring.profiles.active=sqlite
```

That's it — no `docker compose`, no database to provision. A file `./auvex.db` is created next to the
jar on first start (override with `AUVEX_SQLITE_URL`, e.g. `jdbc:sqlite:/var/lib/auvex/auvex.db`).

## What changes under the `sqlite` profile

| Concern | Postgres (default) | `sqlite` profile |
|---|---|---|
| Database | Postgres, schema owned by Flyway migrations | Embedded SQLite file; schema created by Hibernate (`ddl-auto: update`) via the community SQLite dialect, Flyway off |
| Audit chain locking | Per-tenant `pg_advisory_xact_lock` (`PostgresAdvisoryChainLock`) | No-op (`SqliteChainLock`) — SQLite serialises writers at the database level, so appends can't fork the chain |
| Connection pool | Default Hikari pool | `maximum-pool-size: 1` (SQLite is single-writer) |
| Redis cache | Used when enabled | Not required; the response/semantic caches are off by default and fail open |
| Kafka audit pipeline | Optional async sink | Not required; the synchronous audit sink is the default |

The `ChainLock` interface is the seam that makes this work: the audit append code is identical on
both backends, and the right lock implementation is selected by profile. Nothing in this mode
touches the Postgres path — `PostgresAdvisoryChainLock` is active on every profile except `sqlite`.

## Scope & limits

- SQLite is single-writer; this mode targets a single user / low concurrency, not a shared
  deployment. For teams or production, use the default Postgres deployment.
- The schema is created from the JPA mappings rather than the audited Flyway migration history, so
  it is functionally — not byte-for-byte — identical to the Postgres schema.
- Everything the gateway governs (redaction, policy, injection/ moderation screening, the audit hash
  chain, usage/cost) works unchanged; only the storage and locking backend differ.

The persistence core (schema creation, entities, repositories and the audit hash chain) running on
SQLite is covered by `SqlitePersistenceTest`.
