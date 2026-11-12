// Second iOS linkage probe (Issue #91): an app that depends on grant-core ALONE.
//
// Unpublished, like :linkage-probe and desktop-harness. A separate module rather than a second
// framework inside :linkage-probe because Kotlin/Native keeps whatever a framework exports, and
// one module has one public surface. This one must not reach any opt-in module.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "LinkageProbeCore"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":grant-core"))
        }
    }
}
