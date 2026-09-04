import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
    id("maven-publish")
    alias(libs.plugins.dokka)
    alias(libs.plugins.cyclonedx)
}

group = "dev.brewkits"
version = "2.3.0"

kotlin {
    androidTarget {
        publishLibraryVariants("release")

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    jvmToolchain(17)

    // Every public declaration must state its visibility and return type explicitly.
    // The ABI gate below records what the public surface IS; this stops something
    // becoming public by accident in the first place.
    explicitApi()

    // Public API surface lock (KGP 2.4 built-in ABI validation, klib included).
    // Dumps live in api/ and are verified by CI. See grant-core for rationale.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
    }

    // iosX64 dropped in 2.3.0: Compose Multiplatform 1.11 stopped publishing iosX64
    // artifacts, so the target can no longer resolve its compose dependencies. Breaking
    // ONLY for Intel-Mac-simulator consumers of grant-compose; every other module keeps
    // iosX64. (See CHANGELOG 2.3.0.)
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GrantCompose"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Dependency on grant-core for GrantHandler and GrantUiState
            implementation(project(":grant-core"))

            // Compose dependencies
            // The compose.* String accessors are deprecated in the Compose MP Gradle
            // plugin in favour of direct Maven coordinates; the plugin still resolves
            // them to the correct per-target artifacts. Same suppression convention as
            // the consuming KMP-VisionX repo — migration tracked separately.
            @Suppress("DEPRECATION")
            implementation(compose.runtime)
            @Suppress("DEPRECATION")
            implementation(compose.foundation)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Material3 as api - allows apps to control version
            // Apps using Material 2 or different Compose versions can override
            @Suppress("DEPRECATION")
            api(compose.material3)
            @Suppress("DEPRECATION")
            implementation(compose.ui)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.androidx.test.junit)
                implementation(libs.robolectric)
                implementation("androidx.compose.ui:ui-test-junit4:1.7.1")
                implementation("androidx.compose.ui:ui-test-manifest:1.7.1")
            }
        }
    }
}

// 2.3.0: the old koverReport{} 85% rule was REMOVED, not migrated. Under kover 0.7 it never
// actually enforced anything (a real 85% gate would have failed every release — the module's
// single logic test measures 0.0% line coverage under 0.9's merged report, dominated by the
// GrantDialog UI composables). Carrying the number forward would be a fictional floor.
// Kover stays applied for reporting; add a real floor once the dialogs get Robolectric
// compose tests. grant-core's enforced 85% floor is unaffected.

android {

    namespace = "dev.brewkits.grant.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

publishing {
    repositories {
        maven {
            name = "MavenCentralLocal"
            url = uri(layout.buildDirectory.dir("maven-central-staging"))
        }
    }

    publications.configureEach {
        (this as? MavenPublication)?.let {
            groupId = "dev.brewkits"
            version = "2.3.0"

            pom {
                name.set("KMP Grant Compose")
                description.set("Jetpack Compose / Compose Multiplatform UI components for Grant library")
                url.set("https://github.com/brewkits/grant")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("brewkits")
                        name.set("Brewkits")
                        email.set("vietnguyentuan@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/brewkits/Grant.git")
                    developerConnection.set("scm:git:ssh://github.com/brewkits/Grant.git")
                    url.set("https://github.com/brewkits/Grant")
                }
            }
        }
    }
}

// Software Bill of Materials for this published artifact.
// ./gradlew cyclonedxBom  ->  <module>/build/reports/bom.json
//
// Applied per published module rather than at the root: the root task would also walk
// :demo, whose Kotlin/Native and Compose configurations CycloneDX 1.4 cannot resolve, and
// a per-artifact BOM is the right granularity for consumers anyway.
tasks.named<org.cyclonedx.gradle.CycloneDxTask>("cyclonedxBom") {
    // Runtime dependencies are the ones that actually reach a consumer.
    setIncludeConfigs(listOf("releaseRuntimeClasspath"))
    notCompatibleWithConfigurationCache("CycloneDX 1.4 resolves configurations at execution time")
}

// No kover `verify { minBound(...) }` here, deliberately — and this is the reasoning, so the
// absence is not read as an oversight and "fixed" with an arbitrary number.
//
// Measured 2026-09-04: 13/221 lines, of which 178 of the uncovered ones are `@Composable`
// bodies. Kover instruments JVM/Android bytecode, and a composable body only executes under a
// UI-test host (Compose UI test / instrumented), which this module has no runner for. So the
// percentage here reports "how much of this module is not a composable", not how well it is
// tested.
//
// A floor set to the current 5% would gate nothing. A floor set higher would be unreachable
// without a UI-test host, and the cheapest way to satisfy it would be tests written to move the
// number — which is exactly how this module ended up with a single `assertNotNull(AppGrant.CAMERA)`
// placeholder whose own comment said it existed to "contribute to coverage".
//
// What guards this module instead: the ABI dump in api/ pins the public surface, and
// GrantDialogKindTest covers the state->dialog decision — the one piece of real branching logic,
// extracted out of the composables precisely so it could be tested without a host.
