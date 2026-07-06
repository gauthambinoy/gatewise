rootProject.name = "gatewise"

// Only the JVM (Spring Boot) backend is a Gradle subproject.
//
// The React admin console (Phase 4) and the Terraform infrastructure (Phase 5)
// live in their own top-level folders with their own toolchains, so they are
// intentionally NOT Gradle projects and not included here. They get wired into
// the build and CI when their phases begin.
include("gateway")
