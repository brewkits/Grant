plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
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

// Aggregated API reference for the eight published modules (demo is excluded).
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
}

dokka {
    moduleName.set("Grant")
}
