import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

group = "dev.brewkits"
version = "1.0.2"

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
            // Koin for Android (Optional DI support - can be excluded)
            // See docs/DEPENDENCY_MANAGEMENT.md for handling version conflicts
            implementation(libs.koin.android)
            // AndroidX Activity for grant requests
            implementation(libs.androidx.activity.compose)
        }

        commonMain.dependencies {
            // Koin core (Optional DI support - can be excluded)
            // Note: Koin is OPTIONAL. Use GrantFactory.create() for manual injection
            // See docs/DEPENDENCY_MANAGEMENT.md for more information
            implementation(libs.koin.core)
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
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
    repositories {
        maven {
            name = "MavenCentralLocal"
            url = uri("${project.buildDir}/maven-central-staging")
        }
    }

    publications.configureEach {
        (this as? MavenPublication)?.let {
            groupId = "dev.brewkits"
            version = "1.0.2"

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
