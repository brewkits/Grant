rootProject.name = "KMPGrant"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":grant-core")
include(":grant-contacts")
include(":grant-calendar")
include(":grant-motion")
include(":grant-core-koin")
include(":grant-compose")
include(":demo")
include(":grant-bluetooth")
include(":grant-location-always")
include(":grant-tracking")
include(":grant-testing")
include(":grant-bom")
include(":grant-desktop")
// Deliberately not "grant-desktop-harness" — see desktop-harness/build.gradle.kts's header
// comment for why this module must never be mistaken for a published library.
include(":desktop-harness")
