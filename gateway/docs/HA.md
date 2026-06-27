# Multi-region HA, read replicas, partitioned audit (12.3)

This is the durability/scale story for the gateway's Postgres-backed state (tenants, policies,
keys, usage, and the audit log). It splits into two halves: **what runs as code in this repo**
(read/write datasource routing) and **what is a cloud-infrastructure job** (cross-region
replication, global load balancing, managed read replicas). Both are described honestly below —
the gateway cannot replicate a database from inside the JVM, and this doc does not pretend it can.

It is **off by default**. With `auvex.ha.replica.enabled=false` (the default) the gateway uses
Spring Boot's normal single-datasource auto-configuration unchanged, so every existing path and the
live deployment behave exactly as before.

## Read-replica routing (the code in this repo)

Most of the gateway's database work on the hot path is reads: resolving a tenant from an API key,
loading policy, checking budget/quota counters, reading prior audit entries for the hash chain.
Writes are comparatively rare (a new audit row per call, key/policy changes). So the first scaling
lever is to send read-only transactions to a read replica and keep writes on the primary.

### How it works

- `RoutingDataSource` extends Spring's `AbstractRoutingDataSource`. Its `determineCurrentLookupKey()`
  returns `"replica"` when `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` is
  true, and `"primary"` otherwise. That read-only flag is set by Spring whenever a method runs under
  `@Transactional(readOnly = true)`.
- `HaDataSourceConfiguration` builds two real connection pools — the primary from the standard
  `spring.datasource.*` properties, the replica from `auvex.ha.replica.*` — assembles them into a
  `RoutingDataSource` (primary as the default target), and exposes it as the `@Primary DataSource`,
  wrapped in a `LazyConnectionDataSourceProxy`.
- The lazy proxy matters: it defers acquiring the physical connection until the first statement, by
  which point Spring has already set the transaction's read-only flag. Without it, the connection
  would be fetched before the routing key is known and reads would not actually land on the replica.

Because Spring Boot's pooled-datasource auto-configuration backs off the moment a `DataSource` bean
exists, JPA and Flyway transparently use the routing datasource. Flyway migrations and every write
run outside a read-only transaction, so they always hit the primary — the replica is never written
to by the application.

### Enabling it

```yaml
auvex:
  ha:
    replica:
      enabled: true
      url: jdbc:postgresql://auvex-replica.eu-west-1.rds.amazonaws.com:5432/auvex
      username: auvex_ro
      password: <secret>
```

Or via environment variables: `AUVEX_HA_REPLICA_ENABLED=true`, `AUVEX_HA_REPLICA_URL=...`,
`AUVEX_HA_REPLICA_USERNAME=...`, `AUVEX_HA_REPLICA_PASSWORD=...`. If `enabled` is true but no URL is
set, the gateway fails fast at boot rather than starting in a half-configured state.

### Replication-lag caveat

A read replica is asynchronous: it lags the primary by some milliseconds-to-seconds. Two
consequences the code already respects:

- **Read-your-writes** is not guaranteed across the split. Any flow that writes then immediately
  reads its own write must do so in the **same** transaction (or a non-read-only one) so both land on
  the primary. In practice the gateway's writes (audit append, key/policy mutations) are not followed
  by a read-only re-query of the just-written row on the hot path.
- **The audit hash chain is computed during the write** (the append transaction reads the previous
  entry's hash on the primary, then inserts), so chain integrity never depends on the replica being
  current. Read-only audit *queries* (the console, compliance reports) can safely tolerate lag.

Only route a transaction read-only when stale-by-lag data is acceptable for it.

## Partitioned audit

`audit_log` is the table that grows without bound — one row per governed call, retained for the
compliance window (`auvex.compliance.retention-days`). At scale it should be partitioned so that
queries, retention deletes, and index maintenance stay cheap.

### Partition by tenant, then by time

The natural Postgres approach is **declarative partitioning**:

- **By tenant** (`PARTITION BY LIST (tenant_id)` or `HASH (tenant_id)`): each tenant's audit lives in
  its own partition. This aligns perfectly with the gateway's integrity model — **the hash chain is
  per-tenant** (each entry links to the previous entry *for the same tenant*), so a tenant's chain is
  wholly contained in its partition. Chain verification, export, and a tenant's legal-hold/DSAR all
  touch one partition. There is no cross-partition chain to keep consistent.
- **By time** (`PARTITION BY RANGE (created_at)`, e.g. monthly): retention becomes a metadata
  operation — expiring a window is `DROP`/`DETACH PARTITION` instead of a large `DELETE`, which is
  dramatically cheaper and avoids bloat. This composes with tenant partitioning as a sub-partition
  (tenant → month).

**Trade-off:** the hash chain is per-tenant and time-ordered, so a time sub-partition splits one
tenant's chain across monthly partitions. Verification then reads across a tenant's time partitions
in order — fine, because the link is by the previous entry's hash, not by physical co-location. The
one rule: never partition in a way that reorders a tenant's entries, or the chain's "previous hash"
linkage breaks. Tenant-major, time-minor preserves order within each tenant and is the recommended
layout. Pure tenant partitioning (no time split) keeps each chain in a single partition and is the
simplest correct choice when per-tenant volume is moderate.

Retention deletes (`RetentionService`) already operate per-tenant and anchor verification on the
earliest surviving entry, so head-truncation by a `DROP PARTITION` is indistinguishable from normal
retention and does not look like tampering, while any mid-chain edit still fails verification.

This is a schema/migration change (a Flyway migration converting `audit_log` to a partitioned table
plus a partition-maintenance job). It is described here as the intended approach; it is not wired
into the default schema because it is an operational decision tied to real data volume and the
Postgres version in use.

## Multi-region topology

The full picture for surviving a region failure and serving reads close to the caller:

- **One primary region** owns the writable Postgres primary. All writes (audit appends, policy/key
  changes) go there, regardless of where the request lands.
- **Read replicas per region.** Each region runs gateway instances pointed at the local read replica
  via `auvex.ha.replica.*`, so read-heavy work is served locally and only writes cross regions.
- **Region pinning** reuses the existing `auvex.residency` feature: a tenant pinned to a region may
  only reach provider models that run in that region. That governs where *model traffic* may go;
  combined with per-region gateway deployments it keeps a pinned tenant's data plane in-region.
- **Async cross-region audit shipping.** With `auvex.audit.async=true`, audit entries are published
  to Kafka and persisted by a consumer rather than written inline. The same seam lets a region ship
  its audit stream to the primary region's audit store asynchronously, so a remote write never blocks
  a live call and a regional outage does not lose in-flight audit (it drains from the log on
  recovery).

### Failover

- **Replica loss:** the replica is only used for read-only transactions; if it is unreachable, route
  those flows to the primary (operationally, point `auvex.ha.replica.url` at the primary or disable
  the feature) until the replica is restored. No data is at risk — the replica is never authoritative.
- **Primary-region loss:** promote a regional replica to primary (a managed-database failover) and
  repoint the surviving gateways' `spring.datasource.*` at the new primary. The audit hash chain
  survives promotion because it is content-addressed (each entry hashes the previous), so a promoted
  replica that has the entries continues the chain verifiably.
- **Gateway instances are stateless** beyond their datasource and Redis/Kafka connections, so a
  global load balancer can shift traffic between regional fleets without session affinity.

## What is code here vs. what needs cloud infra

**In this repo (works today):**

- `RoutingDataSource` + `HaDataSourceConfiguration`: read-only-transaction → replica routing, behind
  `auvex.ha.replica.enabled`, inert by default.
- The `auvex.residency` region-pinning feature used for region affinity.
- The `auvex.audit.async` Kafka transport used as the cross-region audit-shipping seam.
- The per-tenant audit hash chain that makes replica promotion and partitioning safe.

**Cloud-infrastructure jobs (outside the JVM — you provision these):**

- **The read replica(s) themselves** and physical replication (e.g. RDS/Aurora read replicas, or
  Postgres streaming replication). The gateway connects to a replica; it does not create one.
- **Cross-region replication** of the primary database (e.g. Aurora Global Database) and the
  promote-on-failover mechanism.
- **A global load balancer / DNS** (Route 53 latency or failover routing, a global accelerator) to
  steer callers to the nearest healthy regional fleet and to fail traffic over.
- **The `audit_log` partitioning migration** and its partition-maintenance/retention job, sized to
  the real data volume and Postgres version.
- **Kafka cross-region replication** (e.g. MirrorMaker) if audit is shipped between regions.
