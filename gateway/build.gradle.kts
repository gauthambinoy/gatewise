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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Dependency versions are governed by the Spring Boot BOM (3.5.15) via the
// dependency-management plugin, so we never hand-pick mismatched library versions.
