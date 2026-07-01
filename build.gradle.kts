plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kover) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
    }
}
