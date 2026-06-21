plugins {
    // Versions come from the root build (pinned once there).
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description =
    "Auvex gateway — the proxy hot path that redacts, routes, governs and logs every LLM call."

dependencies {
    // Web stack so the gateway runs as a real HTTP service, plus Actuator for a
    // /actuator/health probe used by the docker-compose healthcheck now and the
    // AWS load balancer later. The proxy / redaction / policy machinery lands in
    // its own roadmap slices, each shipping with its own tests.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Bean Validation (Hibernate Validator) so typed config can be checked at
    // startup — e.g. a missing API key fails the boot instead of a live call.
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Persistence: JPA over Postgres, with Flyway owning the schema via versioned
    // SQL migrations. The JDBC driver is needed only at runtime.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    // Redis-backed response cache so repeat requests can skip the upstream call.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Kafka, for the opt-in async audit pipeline (the audit write never blocks the call).
    implementation("org.springframework.kafka:spring-kafka")
    // BouncyCastle: X.509 generation for the egress proxy's TLS interception (root CA + leaf certs).
    // Not in the Spring Boot BOM, so the version is pinned explicitly.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Integration tests run against a real Postgres in a throwaway container, so
    // they exercise the actual migrations and SQL rather than an in-memory fake.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // A real local HTTP server standing in for the upstream model provider, so the
    // proxy is tested over an actual socket (status passthrough, streaming, timeouts).
    // Pinned explicitly: the Spring Boot BOM doesn't manage mockwebserver's version.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Kafka broker in a container + an await helper, for the async audit pipeline test.
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
}

// Dependency versions are governed by the Spring Boot BOM (3.5.15) via the
// dependency-management plugin, so we never hand-pick mismatched library versions.
