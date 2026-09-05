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

    // Every public declaration must state its visibility and return type explicitly. These
    // fakes were `internal` when they lived duplicated inside each module's own commonTest —
    // making them `public` here is the actual point of this module, so explicitApi() matters
    // more here than almost anywhere else in the project: every method a consumer's test can
    // call is now a real, ABI-locked commitment.
    explicitApi()

    // Public API surface lock (KGP 2.4 built-in ABI validation, klib included). See grant-core
    // for the full rationale. A test-double library's surface is exactly the kind of thing that
    // must not drift silently: a consumer's test suite breaking on a patch bump would be worse
    // here than almost anywhere else, since it fails in CI, not at runtime.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
    }

    // Matches grant-core's full target set exactly (android, jvm, js, wasmJs, 3 iOS
    // targets) — not a subset. This module is consumed from grant-core's own commonTest
    // (see grant-core/build.gradle.kts), which runs on every target grant-core has; a
    // target missing here would fail grant-core's test dependency resolution for that
    // target only, not at configure time, so it's easy to miss without checking directly.
    js {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GrantTesting"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: FakeGrantManager/FakeServiceManager/FakeGrantStore
            // implement grant-core's public interfaces and use its public types
            // (GrantPermission, GrantStatus, ServiceType, ...) directly in their own public
            // signatures, so a consumer adding grant-testing needs those types visible too —
            // same reasoning grant-core-koin already applies to this same dependency.
            api(project(":grant-core"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "dev.brewkits.grant.testing"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Maven Central rejects a `jar`-packaged artifact with no javadoc jar (see grant-core's
// identical comment — the v2.4.0 upload failed on exactly this for grant-core-jvm). This
// module's jvm() target is a plain jar publication too, so it needs the same fix, wired up
// from day one rather than discovered the hard way a second time.
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
                name.set("KMP Grant Testing")
                description.set("Official test doubles (FakeGrantManager, FakeServiceManager, MultiGrantFakeManager, FakeGrantStore) for testing code that depends on KMP Grant")
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
tasks.named<org.cyclonedx.gradle.CycloneDxTask>("cyclonedxBom") {
    setIncludeConfigs(listOf("releaseRuntimeClasspath"))
    notCompatibleWithConfigurationCache("CycloneDX 1.4 resolves configurations at execution time")
}

// Unlike the iOS-only opt-in modules, this one's logic (map lookups, default fallbacks, call
// tracking) is plain Kotlin that runs identically on the Android/JVM target kover measures — a
// floor here is not the "100% on an untestable no-op" trap documented in those other modules'
// build.gradle.kts files. Floor set from a real measurement, not a guess: see the commonTest
// suite for what's covered.
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
