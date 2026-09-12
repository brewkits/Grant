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
            baseName = "GrantCoreKoin"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":grant-core"))
            api(libs.koin.core)
        }
        
        androidMain.dependencies {
            api(libs.koin.android)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.koin.test)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.koin.test)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.core)
            }
        }
    }
}

// Real Kover floor, not a guessed round number (Issue #80).
//
// Unlike the iOS-only opt-in modules, this module's content — GrantModule.kt (commonMain) and
// GrantPlatformModule.android.kt — compiles to JVM/Android bytecode Kover can actually measure.
// Measured 2026-09-12 via `./gradlew :grant-core-koin:koverXmlReport` (exact LINE counters, not
// the HTML report's Instruction% column, which reads higher and is easy to misread as Line%):
// 87.5% line coverage (14/16), up from 50% (8/16) after adding
// `GrantPlatformModuleAndroidTest` (Robolectric, real Android `Context`), which closed the
// `GrantPlatformModule_androidKt` gap from 0% to 100% (6/6) — its `single { }` needed a real
// `Context`, which no test in this module previously provided. GrantModuleKt sits at 8/10 lines
// (the 2 misses are exercised only by resolution paths not currently under test). Floor set at
// the measured line coverage so a regression is caught.
kover {
    reports {
        verify {
            rule {
                minBound(87)
            }
        }
    }
}

android {
    namespace = "dev.brewkits.grant.koin"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

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
        version = "2.5.0"

        pom {
            name.set("KMP Grant Koin")
            description.set("Koin dependency injection support for KMP Grant")
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
