// Tier 2's (ROADMAP.md v2.6.0) manual verification harness for the macOS TCC bridge.
//
// DELIBERATELY UNPUBLISHED: no `maven-publish` plugin, no `publishing {}` block, no
// `version`, and named without the `grant-` prefix precisely so it is never mistaken for a
// library — `grant-*` in settings.gradle.kts is the signal `create-grant-maven-bundle-auto.sh`
// and its `MODULES` array key off. Do not add this module to that array.
//
// Plain `application` + Compose Desktop, not Kotlin Multiplatform: this only ever needs to
// run as a real macOS JVM process, so `jvm("desktop")` inside the KMP `demo` module was tried
// first and reverted — `demo`'s shared `commonMain` carries seven Android/iOS-only project
// dependencies (grant-compose, grant-core-koin, grant-contacts, ...) that have no `jvm`
// variant, so adding a JVM target there broke dependency resolution for the whole module. A
// separate, single-target module has none of that.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":grant-core"))
    implementation(project(":grant-desktop"))
    @Suppress("DEPRECATION")
    implementation(compose.desktop.currentOs)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
}

// The only run target that proves anything for Tier 2: `:desktop-harness:runDistributable`
// launches this from a genuinely packaged, code-identified `.app` bundle (with
// `NSCameraUsageDescription` set below), which is what gives macOS TCC something to attribute
// the camera prompt to. `:desktop-harness:run` (unbundled) is a useful *negative* control —
// if the same dialog appears there, the harness is not isolating what it claims to.
compose.desktop {
    application {
        mainClass = "dev.brewkits.grant.desktop.harness.CameraHarnessKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "GrantDesktopHarness"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "dev.brewkits.grant.desktopharness"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>Grant's Tier 2 macOS harness needs the camera to verify the real TCC consent flow end-to-end.</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Grant's Tier 2 macOS harness needs the microphone to verify the real TCC consent flow end-to-end.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
