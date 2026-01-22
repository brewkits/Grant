import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

group = "dev.brewkits"
version = "1.0.0"

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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GrantCore"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            // Koin for Android
            implementation(libs.koin.android)
            // AndroidX Activity for grant requests
            implementation(libs.androidx.activity.compose)
        }

        commonMain.dependencies {
            // Koin core
            implementation(libs.koin.core)
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Moko Grants (OPTIONAL - only needed if using MokograntManager)
            // Custom implementation is now the default and doesn't require moko dependencies
            // Uncomment these if you want to use Moko implementation:
            // api(libs.moko.grants)
            // api(libs.moko.grants.camera)
            // api(libs.moko.grants.gallery)
            // api(libs.moko.grants.storage)
            // api(libs.moko.grants.location)
            // api(libs.moko.grants.contacts)
            // api(libs.moko.grants.microphone)
            // api(libs.moko.grants.bluetooth)
            // api(libs.moko.grants.notifications)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "dev.brewkits.grant"
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
    publications {
        withType<MavenPublication> {
            groupId = "dev.brewkits"
            version = "1.0.0"

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
            }
        }
    }
}
