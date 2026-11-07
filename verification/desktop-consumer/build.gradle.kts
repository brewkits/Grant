plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(17) }
val grantVersion = providers.gradleProperty("grantVersion").get()
dependencies {
    implementation(platform("dev.brewkits:grant-bom:$grantVersion"))
    implementation("dev.brewkits:grant-core")
    implementation("dev.brewkits:grant-desktop")
    implementation(libs.kotlinx.coroutines.core)
    @Suppress("DEPRECATION")
    implementation(compose.desktop.currentOs)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "ConsumerKt"
        nativeDistributions {
            packageName = "GrantDesktopConsumer"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "dev.brewkits.grant.desktopconsumer"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>Verify Grant camera consent in an independent consumer.</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Verify Grant microphone consent in an independent consumer.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
