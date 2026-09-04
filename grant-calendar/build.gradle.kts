import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GrantCalendar"
            isStatic = true
            linkerOpts("-weak_framework", "EventKit")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":grant-core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

android {
    namespace = "dev.brewkits.grant.calendar"
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
                name.set("KMP Grant Calendar")
                description.set("Calendar permission handler for KMP Grant")
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

// No kover `verify { minBound(...) }` here, deliberately.
//
// Measured 2026-09-04: kover reports 1/1 lines = 100% for this module, and that number is
// meaningless. The only class it instruments is the Android `Grant*` entry point, whose
// `initialize()` is a documented no-op; the actual logic — the iOS handler that maps the
// platform's authorization status — is Kotlin/Native, which kover does not measure at all.
//
// A floor would therefore pass at 100% forever while the code that matters stays unmeasured.
// The real safety net for this module is its iosTest suite, which exercises the handler on a
// simulator.
