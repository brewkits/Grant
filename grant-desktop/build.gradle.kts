import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("maven-publish")
    alias(libs.plugins.dokka)
    alias(libs.plugins.cyclonedx)
}

group = "dev.brewkits"
version = "3.0.0-alpha01"

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
                api(project(":grant-core")) // AppGrant appears in the public capability API.
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
            resources.srcDir(layout.buildDirectory.dir("generated/windowsResources"))
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

val isWindowsHost = System.getProperty("os.name").startsWith("Windows")
val windowsBridgeInput = providers.gradleProperty("grantWindowsBridgeDir")
val configureWindowsBridge by tasks.registering(Exec::class) {
    onlyIf { isWindowsHost && !windowsBridgeInput.isPresent }
    inputs.dir("src/windowsMain/cpp")
    outputs.file(layout.buildDirectory.file("windows-native/CMakeCache.txt"))
    commandLine("cmake", "-S", file("src/windowsMain/cpp"), "-B",
        layout.buildDirectory.dir("windows-native").get().asFile, "-A", "x64")
}
val buildWindowsBridge by tasks.registering(Exec::class) {
    dependsOn(configureWindowsBridge)
    onlyIf { isWindowsHost && !windowsBridgeInput.isPresent }
    inputs.dir("src/windowsMain/cpp")
    outputs.file(layout.buildDirectory.file("windows-native/Release/GrantWindowsBridge.dll"))
    commandLine("cmake", "--build", layout.buildDirectory.dir("windows-native").get().asFile,
        "--config", "Release")
}
val copyWindowsBridge by tasks.registering(Sync::class) {
    dependsOn(buildWindowsBridge)
    // Sync clears old inputs when a checkout stops importing a Windows artifact.
    if (windowsBridgeInput.isPresent) {
        from(windowsBridgeInput) { include("GrantWindowsBridge.dll") }
    } else if (isWindowsHost) {
        from(layout.buildDirectory.dir("windows-native/Release")) { include("GrantWindowsBridge.dll") }
    }
    into(layout.buildDirectory.dir("generated/windowsResources/win32-x86-64"))
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(copyMacosArm64Dylib, copyWindowsBridge)
}

// JVM publications need real documentation as well as the sources JAR supplied by KGP.
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}

val desktopSbom = tasks.named<org.cyclonedx.gradle.CycloneDxTask>("cyclonedxBom") {
    setIncludeConfigs(listOf("jvmRuntimeClasspath"))
    notCompatibleWithConfigurationCache("CycloneDX resolves configurations at execution time")
}

// Linux CI can compile/test the JVM fallback, but cannot produce a distributable macOS bridge.
val verifyDesktopPublication by tasks.registering {
    dependsOn("jvmJar", dokkaJavadocJar, desktopSbom)
    doLast {
        check(System.getProperty("os.name").startsWith("Mac") &&
            System.getProperty("os.arch") in listOf("aarch64", "arm64")) {
            "Publish grant-desktop from an Apple Silicon Mac; other hosts cannot ship the bridge."
        }
        val windowsInput = windowsBridgeInput.orNull?.let { file(it).resolve("GrantWindowsBridge.dll") }
        check(windowsInput != null && windowsInput.isFile) {
            "Set -PgrantWindowsBridgeDir to the Windows CI artifact for this source revision."
        }
        // The DLL must have been built from THIS source revision, not merely be "a DLL that passed
        // CI once". The Windows CI job records `git rev-parse HEAD` beside the DLL.
        val revisionFile = windowsInput.resolveSibling("bridge-source-revision.txt")
        check(revisionFile.isFile) {
            "Missing bridge-source-revision.txt next to the Windows DLL. Use the CI artifact for this " +
                "commit, or write `git rev-parse HEAD` of the commit the DLL was built from into that file."
        }
        val bridgeRevision = revisionFile.readText().trim()
        val sourceRevision = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(rootProject.projectDir).redirectErrorStream(true).start()
            .let { process -> process.inputStream.bufferedReader().readText().trim().also { process.waitFor() } }
        check(bridgeRevision == sourceRevision) {
            "The Windows bridge was built from $bridgeRevision but this checkout is $sourceRevision. " +
                "Download the artifact from the CI run for this exact commit."
        }
        val jar = tasks.named<Jar>("jvmJar").get().archiveFile.get().asFile
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("darwin-aarch64/libGrantDesktopBridge.dylib")
            check(entry != null && entry.size > 0) { "Desktop JVM JAR is missing its macOS native bridge" }
            val windows = zip.getEntry("win32-x86-64/GrantWindowsBridge.dll")
            check(windows != null && windows.size > 0) {
                "Desktop publication requires the tested Windows x64 DLL. Set -PgrantWindowsBridgeDir=<CI artifact directory>."
            }
            check(zip.getInputStream(windows).use { it.readBytes() }.contentEquals(windowsInput.readBytes())) {
                "Bundled Windows bridge differs from the selected CI artifact"
            }
        }
        ZipFile(dokkaJavadocJar.get().archiveFile.get().asFile).use { zip ->
            check(zip.entries().asSequence().any { it.name.endsWith(".html") }) {
                "Desktop javadoc JAR must contain generated documentation"
            }
        }
        check(layout.buildDirectory.file("reports/bom.json").get().asFile.isFile) {
            "Desktop publication is missing its SBOM"
        }
    }
}

tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
    dependsOn(verifyDesktopPublication)
}

publishing {
    repositories {
        maven {
            name = "MavenCentralLocal"
            url = uri(layout.buildDirectory.dir("maven-central-staging"))
        }
    }

    publications.named<MavenPublication>("jvm") {
        artifact(dokkaJavadocJar)
        artifact(layout.buildDirectory.file("reports/bom.json")) {
            classifier = "cyclonedx"
            extension = "json"
            builtBy(desktopSbom)
        }
    }

    publications.configureEach {
        (this as? MavenPublication)?.let {
            groupId = "dev.brewkits"
            version = "3.0.0-alpha01"

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
