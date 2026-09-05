plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.cyclonedx) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
    }
}

// Aggregated API reference for the library modules (demo and desktop-harness are excluded —
// the latter is a verification harness, deliberately unpublished; see its build.gradle.kts).
// Build with: ./gradlew dokkaGenerate  ->  build/dokka/html
dependencies {
    dokka(project(":grant-core"))
    dokka(project(":grant-compose"))
    dokka(project(":grant-core-koin"))
    dokka(project(":grant-contacts"))
    dokka(project(":grant-calendar"))
    dokka(project(":grant-motion"))
    dokka(project(":grant-bluetooth"))
    dokka(project(":grant-location-always"))
    dokka(project(":grant-tracking"))
    dokka(project(":grant-testing"))
    dokka(project(":grant-desktop"))
    // grant-bom is deliberately absent — a pure Maven BOM (java-platform plugin) has no
    // Kotlin code and no Dokka plugin applied.
}

dokka {
    moduleName.set("Grant")
}
