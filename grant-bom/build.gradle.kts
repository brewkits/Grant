// A Maven BOM (Bill of Materials), not a code module — pure Gradle `java-platform`, no Kotlin
// plugin. Grant is 10 modules that must always move in lockstep (see this repo's own
// create-grant-maven-bundle-auto.sh, which bumps all of them from one VERSION variable); this
// lets a *consumer* get the same guarantee without retyping "2.5.0" on every line:
//
//   dependencies {
//       implementation(platform("dev.brewkits:grant-bom:2.5.0"))
//       implementation("dev.brewkits:grant-core")          // no version needed
//       implementation("dev.brewkits:grant-compose")       // no version needed
//       implementation("dev.brewkits:grant-tracking")      // no version needed
//   }
//
// Updating one version (this file's) instead of N call sites is also what prevents the
// mixed-version failure mode CLAUDE.md already warns about for AppGrant ordinals: "mix
// modules compiled against different Grant versions" is exactly the case a BOM exists to rule
// out for consumers who use it.
plugins {
    `java-platform`
    `maven-publish`
}

group = "dev.brewkits"
version = "2.5.0"

// This module has no dependencies of its own to declare transitively — it exists purely to
// pin sibling versions — so the default (closed) constraint set is correct; javaPlatform.
// allowDependencies() is for a BOM that itself depends on another BOM, which doesn't apply here.

dependencies {
    constraints {
        // project(...) rather than a hardcoded "dev.brewkits:grant-core:2.5.0" string: Gradle's
        // publish plugin resolves each project reference to that project's own group/name/version
        // when generating the POM, so this file cannot drift from the real published coordinates
        // the way a hand-typed string list could.
        api(project(":grant-core"))
        api(project(":grant-compose"))
        api(project(":grant-core-koin"))
        api(project(":grant-contacts"))
        api(project(":grant-calendar"))
        api(project(":grant-motion"))
        api(project(":grant-bluetooth"))
        api(project(":grant-location-always"))
        api(project(":grant-tracking"))
        api(project(":grant-testing"))
        // grant-desktop is deliberately absent — it is Gradle-published only, not on Maven
        // Central (see its own build.gradle.kts and create-grant-maven-bundle-auto.sh's
        // MODULES comment), so it has no Maven coordinate for a BOM to pin.
    }
}

publishing {
    repositories {
        maven {
            name = "MavenCentralLocal"
            url = uri(layout.buildDirectory.dir("maven-central-staging"))
        }
    }

    publications {
        create<MavenPublication>("mavenPlatform") {
            from(components["javaPlatform"])

            groupId = "dev.brewkits"
            artifactId = "grant-bom"
            version = "2.5.0"

            pom {
                name.set("KMP Grant BOM")
                description.set("Bill of Materials pinning matching versions of every published KMP Grant module")
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
