import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

group = "dev.brewkits"
version = "2.2.0"

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
        version = "2.2.0"

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


