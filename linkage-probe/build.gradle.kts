// iOS linkage probe for Issue #91: what does grant-core drag into a CONSUMER's binary?
//
// DELIBERATELY UNPUBLISHED, like desktop-harness: no maven-publish, no version, and no
// `grant-` prefix, so create-grant-maven-bundle-auto.sh's MODULES array never picks it up.
//
// Why a separate module rather than inspecting :grant-core's own GrantCore framework: a
// framework built FROM grant-core exports grant-core's whole public API, so every handler is
// reachable and every framework shows up. That says nothing about an app. This module plays
// the app: it depends on grant-core only, and its public surface (LinkageProbe.kt) uses only
// what the #91 reporter's camera-only app uses. scripts/check-ios-linkage.sh then inspects the
// release framework built here.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "LinkageProbe"
            // Matches the #91 reporter's setup (static framework, release archive).
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":grant-core"))
            // Since 3.0.0 CAMERA needs grant-camera; the probe adds exactly what a camera-only app adds.
            implementation(project(":grant-camera"))
        }
    }
}
