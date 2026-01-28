import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            baseName = "GrantCompose"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Dependency on grant-core for GrantHandler and GrantUiState
            implementation(project(":grant-core"))

            // Compose dependencies
            implementation(compose.runtime)
            implementation(compose.foundation)
            // Material3 is compile-only - users must provide their own version
            // This prevents version conflicts and allows users to control their Compose version
            compileOnly(compose.material3)
            implementation(compose.ui)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.brewkits.grant.compose"
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
    publications.configureEach {
        (this as? MavenPublication)?.let {
            groupId = "dev.brewkits"
            version = "1.0.0"

            pom {
                name.set("KMP Grant Compose")
                description.set("Jetpack Compose / Compose Multiplatform UI components for Grant library")
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
