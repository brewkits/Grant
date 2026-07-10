import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget("android") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    jvmToolchain(17)

    // iosX64 dropped with the Compose 1.11 bump (no iosX64 compose artifacts) — the demo
    // consumes grant-compose, which also dropped the target in 2.3.0.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "GrantDemoShared"
            isStatic = true

            // Add this line to fix the warning:
            freeCompilerArgs += listOf("-Xbinary=bundleId=dev.brewkits.grant.demo.shared")
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }

        commonMain.dependencies {
            // The compose.* String accessors are deprecated in the Compose MP Gradle
            // plugin in favour of direct Maven coordinates; the plugin still resolves
            // them to the correct per-target artifacts. Same suppression convention as
            // the consuming KMP-VisionX repo — migration tracked separately.
            @Suppress("DEPRECATION")
            implementation(compose.runtime)
            @Suppress("DEPRECATION")
            implementation(compose.foundation)
            @Suppress("DEPRECATION")
            implementation(compose.material3)
            @Suppress("DEPRECATION")
            implementation(compose.ui)
            @Suppress("DEPRECATION")
            implementation(compose.components.resources)
            @Suppress("DEPRECATION")
            implementation(compose.components.uiToolingPreview)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Grant library
            implementation(project(":grant-core"))
            implementation(project(":grant-core-koin"))
            implementation(project(":grant-compose"))

            // Optional permission modules (required for Contacts/Calendar/Motion on iOS)
            implementation(project(":grant-contacts"))
            implementation(project(":grant-calendar"))
            implementation(project(":grant-motion"))
            implementation(project(":grant-bluetooth"))
            implementation(project(":grant-location-always"))
        }

        androidMain.dependencies {
            // AndroidX Core for NotificationManagerCompat
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "dev.brewkits.grant.demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.brewkits.grant.demo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
