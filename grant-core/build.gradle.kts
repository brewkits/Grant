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
version = "2.5.0"

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
    // Two breaking changes shipped unnoticed in v2.1.0 (GrantDialogStrings, the
    // IosPermissionHandler -> PermissionHandler rename) because nothing compared the
    // surface between releases. Dumps live in api/ and are checked in CI.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        // Calling this block enables validation; klib dumps are always produced.
    }

    // Browser targets — classic JS and Kotlin/Wasm, both consent-real via
    // navigator.permissions / getUserMedia / Notification / geolocation, not a stub.
    // wasmJs exists specifically because Compose Multiplatform Web targets it, not the
    // classic js backend; shipping js-only under a "web" label would leave every
    // Compose Web consumer unable to link this artifact at all.
    // See webMain's PlatformGrantDelegate.web.kt for the per-AppGrant mapping and its
    // documented gaps (permissions with no browser equivalent resolve to DENIED_ALWAYS,
    // never a fabricated GRANTED — a web app cannot silently gain contacts/calendar
    // access just because grant-core compiles here).
    js {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Desktop bridge target for Tier 2 (macOS via Compose Desktop) — see ROADMAP.md v2.6.0.
    // This is deliberately a MINIMAL actual: jvmMain's PlatformGrantDelegate reports every
    // permission unsupported (DENIED_ALWAYS + log) unless a real handler has been registered
    // into DesktopPermissionHandlerRegistry. It is the `grant-desktop` module (JNA + a
    // Kotlin/Native macOS dylib bridging AVFoundation/CoreLocation/Contacts/EventKit — not
    // hand-rolled objc_msgSend, which cannot safely receive Objective-C completion-handler
    // blocks) that registers real handlers when added, the same opt-in-module pattern
    // grant-contacts/grant-calendar/grant-motion already use for iOS. A consumer that adds
    // only grant-core and calls jvm() gets the honest-unsupported delegate, never a fabricated
    // GRANTED for a permission this module alone cannot back.
    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GrantCore"
            isStatic = true
            // Fix Issue #25: Use weak-linked frameworks for sensitive permissions.
            // This provides an additional layer of safety to prevent Apple from
            // flagging unused frameworks during App Store static analysis scans.
            linkerOpts("-weak_framework", "CoreLocation")
            linkerOpts("-weak_framework", "Photos")
            linkerOpts("-weak_framework", "AVFoundation")
        }
    }

    sourceSets {
        androidMain.dependencies {
            // AndroidX Activity for grant requests
            implementation(libs.androidx.activity.compose)
            implementation("androidx.fragment:fragment:1.8.9")
        }

        commonMain.dependencies {
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
            implementation(project(":grant-testing"))
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
            implementation(libs.kotlinx.coroutines.test)
        }

        // JVM-based Android unit tests (no device/emulator required)
        // Tests permission mapping logic, API version branching, and status reporting
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.core)
            }
        }
    }
}

// Kover 0.9 DSL (migrated from the 0.7 koverReport{} block, 2.3.0 toolchain bump).
// One rule guards the merged report — same 85% line-coverage floor the 0.7 config
// enforced for both the default and the debug Android report.
kover {
    reports {
        // NOTE: this must be `total.filtersAppend` — the top-level `reports.filters { }`
        // block did NOT affect koverVerify/koverXmlReport in 0.9.8 (verified empirically:
        // the excluded class stayed in the report). koverVerify runs against the TOTAL
        // report set, which carries its own filter config.
        total {
            filtersAppend {
                excludes {
                    // OS-driven transparent Activity: only exercisable by an on-device
                    // instrumented test, which kover does not measure. Kover 0.7's default
                    // report did not count it either — excluding keeps the 85% floor
                    // comparable across the 0.7 → 0.9 migration instead of silently
                    // re-basing the bar. Wildcard also drops its lambdas/companion.
                    classes("dev.brewkits.grant.impl.GrantRequestActivity*")
                }
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

android {
    namespace = "dev.brewkits.grant"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    testOptions {
        unitTests.all {
            // Robolectric loads a full android-all jar per @Config(sdk = ...) level, in the
            // same JVM. The suite spans API 21 through 34, and adding one more multi-SDK
            // class was enough to OOM the default test heap:
            //   OutOfMemoryError: Failed to load .../android-all-instrumented-9-...jar
            // org.gradle.jvmargs in gradle.properties sizes the Gradle daemon, NOT this
            // forked test JVM, so the heap has to be set here.
            it.maxHeapSize = "2g"
        }
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Maven Central rejects a `jar`-packaged artifact with no javadoc jar (confirmed the hard way:
// the v2.4.0 upload failed Sonatype's Publisher Portal validation on exactly this — 83/84
// components passed, only `grant-core-jvm` failed with "Javadocs must be provided but not found
// in entries"). The other 8 published modules' AAR/klib artifacts don't need one — this project
// never had a jar-packaged JVM artifact before the jvm() target landed in 2.4.0, so the gap was
// invisible until then. Real Dokka HTML output, not an empty stub, reusing the plugin already
// applied to this module.
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}

publishing {
    repositories {
        maven {
            name = "MavenCentralLocal"
            url = uri(layout.buildDirectory.dir("maven-central-staging"))
        }
    }

    publications.named("jvm", MavenPublication::class) {
        artifact(dokkaJavadocJar)
    }

    publications.configureEach {
        (this as? MavenPublication)?.let {
            groupId = "dev.brewkits"
            version = "2.5.0"

            pom {
                name.set("KMP Grant")
                description.set("A clean, wrapper-based grant management library for Kotlin Multiplatform")
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
