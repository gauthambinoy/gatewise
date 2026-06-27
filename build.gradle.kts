import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    // Plugin versions are pinned ONCE here. Modules opt in below / in their own
    // build files using the same ids without a version. `apply false` means the
    // root project itself doesn't apply them — it only owns the versions.
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "8.7.0" apply false
    id("com.github.spotbugs") version "6.5.8" apply false
}

// Everything below is the SHARED quality baseline applied to every JVM module,
// so no module can quietly drift from the standard. Add a module to
// settings.gradle.kts and it automatically inherits all of this.
subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")

    repositories { mavenCentral() }

    // One Java toolchain for the whole repo: Temurin 21 (LTS). Gradle downloads
    // it automatically on machines that don't have it, so builds are reproducible.
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    // Spotless owns formatting (Google Java Format) so style is automatic and
    // never argued about in review. `./gradlew spotlessApply` fixes everything.
    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat()
            removeUnusedImports()
            importOrder()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // Checkstyle enforces the rules Spotless can't (naming, design, bug-prone
    // patterns). Config lives once at config/checkstyle and is shared by all.
    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.21.0"
        configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
        isIgnoreFailures = false
        maxWarnings = 0
    }

    // SpotBugs: static analysis over compiled bytecode for real bug patterns.
    // Pinned to 4.8.6 on purpose: the 4.9.x line ships Apache BCEL 6.10, which
    // calls a commons-lang3 `Strings` API that isn't on its own classpath and
    // crashes every analysis (NoClassDefFoundError). 4.8.6 is the last clean
    // release and fully supports Java 21 bytecode.
    extensions.configure<SpotBugsExtension> {
        toolVersion.set("4.8.6")
        effort.set(Effort.MAX)
        reportLevel.set(Confidence.MEDIUM)
        excludeFilter.set(
            rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml").asFile,
        )
    }
    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required.set(true) }
        reports.create("xml") { required.set(true) }
    }

    // JaCoCo produces a coverage report on every test run. The fail-the-build
    // coverage threshold is added once there is real logic to cover (Phase 1+).
    extensions.configure<JacocoPluginExtension> { toolVersion = "0.8.12" }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }
    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
