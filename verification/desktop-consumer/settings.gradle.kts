pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
rootProject.name = "grant-desktop-consumer"
val grantRoot = rootDir.resolve("../..").canonicalFile
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files(grantRoot.resolve("gradle/libs.versions.toml"))) } }
    repositories {
        // All Grant coordinates must come from staged artifacts, never project substitution
        // or a cached public release. Other dependencies still resolve normally.
        exclusiveContent {
            forRepositories(
                maven { url = uri(grantRoot.resolve("grant-core/build/maven-central-staging")) },
                maven { url = uri(grantRoot.resolve("grant-desktop/build/maven-central-staging")) },
                maven { url = uri(grantRoot.resolve("grant-bom/build/maven-central-staging")) },
            )
            filter { includeGroup("dev.brewkits") }
        }
        google()
        mavenCentral()
    }
}
