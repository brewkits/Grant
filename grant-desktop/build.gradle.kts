// Plugin set differs from the other published modules on purpose, not by omission:
//  - `dokka` is applied (this module has real public API: GrantDesktop) and it is wired into
//    the root aggregation alongside the other eight.
//  - `cyclonedx` is NOT applied. Every other module configures its SBOM task against
//    `releaseRuntimeClasspath`, an Android configuration; this module has no Android target,
//    so copying that block would fail at configuration time. It gets an SBOM when the module
//    is actually wired for publishing (ROADMAP.md v2.6.0's remaining Tier 2 items).
//  - `kover` is NOT applied. A coverage floor on a module that is deliberately a thin bridge —
//    most of whose behaviour can only be exercised by a signed .app talking to real TCC, not by
//    a Gradle test JVM — would measure the wrong thing and invite gaming the number.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("maven-publish")
    alias(libs.plugins.dokka)
}

group = "dev.brewkits"
version = "2.3.0"

kotlin {
    jvmToolchain(17)
    explicitApi()

    // Public API surface lock (KGP 2.4 built-in ABI validation). See grant-core for rationale.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
    }

    // The JVM side: loads the macOS dylib below via JNA and registers real handlers into
    // grant-core's DesktopPermissionHandlerRegistry. This is the ONLY target that ships JNA —
    // see the dependency block below and ROADMAP.md v2.6.0's isolation rule.
    jvm()

    // macOS Kotlin/Native shared library — NOT the standalone macosArm64 klib target the
    // multi-platform plan rejects elsewhere. A klib is consumed by another Kotlin/Native or
    // Swift build, which a JVM Compose Desktop app cannot do; a .dylib loaded over JNA has no
    // such restriction. `macosMain` cinterops AVFoundation directly — the same typed cinterop
    // `iosMain` already uses, just macOS's copy of the framework — so an Objective-C
    // completion-handler parameter is a plain Kotlin lambda here, never a hand-built
    // Block_literal struct.
    //
    // arm64 only for now, deliberately: this machine is arm64, and the plan's own bar is
    // "verified on the actual OS" — an x64 slice built but never run on real Intel/Rosetta
    // hardware would be exactly the unverified claim this expansion exists to avoid.
    // `macosX64()` is also KGP-deprecated as of this Kotlin version ("target will be removed
    // in a future release"), which is a second, independent reason not to add it speculatively.
    // Tracked in ROADMAP.md v2.6.0 rather than silently dropped.
    macosArm64().binaries.sharedLib {
        baseName = "GrantDesktopBridge"
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":grant-core"))
                implementation(libs.jna)
                // grant-core exposes this as `implementation`, not `api` — not transitive,
                // needs its own declaration here too.
                implementation(libs.kotlinx.coroutines.core)
            }
            // Bundles the compiled macOS dylib(s) as JVM resources so a plain Maven dependency
            // on grant-desktop carries the native bridge with it — no separate native-artifact
            // download or manual dylib placement required. Path convention (`darwin-aarch64/`)
            // mirrors JNA's own bundled-native-lib layout, read by NativeBridgeLoader.kt.
            resources.srcDir(layout.buildDirectory.dir("generated/darwinResources"))
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

val copyMacosArm64Dylib by tasks.registering(Copy::class) {
    dependsOn("linkReleaseSharedMacosArm64")
    from(layout.buildDirectory.dir("bin/macosArm64/releaseShared")) {
        include("libGrantDesktopBridge.dylib")
    }
    into(layout.buildDirectory.dir("generated/darwinResources/darwin-aarch64"))
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(copyMacosArm64Dylib)
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
                name.set("KMP Grant Desktop")
                description.set("macOS (Compose Desktop / JVM) permission handlers for KMP Grant")
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
