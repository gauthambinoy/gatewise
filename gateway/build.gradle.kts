plugins {
    // Versions come from the root build (pinned once there).
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description =
    "Auvex gateway — the proxy hot path that redacts, routes, governs and logs every LLM call."

dependencies {
    // Core Spring context only for now. Web, data and the proxy machinery arrive
    // in their own roadmap slices so each ships with its own tests.
    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Dependency versions are governed by the Spring Boot BOM (3.5.15) via the
// dependency-management plugin, so we never hand-pick mismatched library versions.
