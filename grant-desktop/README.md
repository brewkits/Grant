# Grant Desktop preview

**Development version: 2.7.0-alpha01. Not yet uploaded to Maven Central.**
The 2.6.0 release does not publish `grant-desktop`. This preview adds its publication,
BOM constraint, native packaging guard, generated JVM javadoc and JVM dependency SBOM.

## Scope

| Capability | Preview implementation |
| --- | --- |
| Host | JVM 17 on macOS Apple Silicon (`aarch64` / `arm64`) |
| Camera / microphone | AVFoundation authorization status and native consent request |
| Settings | Opens the Camera privacy pane |
| Other permissions | No built-in desktop handlers; core returns `DENIED_ALWAYS` |
| Intel macOS / Windows / Linux | No native bridge bundled |

This is a core/bridge preview. `grant-compose` and `grant-core-koin` do not have JVM
variants; supply your own Desktop UI and DI. JNA is confined to the Desktop module.
The bridge does not capture audio/video or add permission declarations to the host app.

## Dependency and initialization

After publishing the preview to a repository available to your app:

```kotlin
kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(platform("dev.brewkits:grant-bom:2.7.0-alpha01"))
                implementation("dev.brewkits:grant-core")
                implementation("dev.brewkits:grant-desktop")
            }
        }
    }
}
```

For development now, use the [staged artifact consumer](../verification/desktop-consumer/README.md).
It resolves these coordinates locally without requiring a Central upload.

```kotlin
import dev.brewkits.grant.GrantFactory
import dev.brewkits.grant.desktop.GrantDesktop

fun main() {
    GrantDesktop.initialize() // Before creating/requesting through the permission flow.
    val grantManager = GrantFactory.create()
    // Gate camera/microphone UI with GrantDesktop.isSupported(AppGrant.CAMERA).
    // Use grantManager from your app's coroutine scope and UI.
}
```

Without a loaded bridge, `DENIED_ALWAYS` can mean unsupported/unavailable, not a user's
actual refusal. `GrantDesktop.isSupported(AppGrant.CAMERA)` checks bridge availability
without showing a prompt; it does not check user authorization or physical device presence.
Limit your UI to supported capabilities and enable
GrantLogger during integration. No new permission status enum values are introduced here.

## Packaging and consent

Provide `NSCameraUsageDescription` and `NSMicrophoneUsageDescription` in the host app's
Info.plist. Launch the packaged `.app` through Finder or `open`; a bare Java process or
direct execution from a terminal may read the launching process's consent instead.

For distribution, follow Apple's usage-description and entitlement requirements and
JetBrains' signing/notarization instructions. In particular, verify the extracted JNA and
Grant native libraries under your hardened-runtime/library-validation policy. A successful
development launch is not evidence of notarized or sandboxed compatibility. Do not change
security entitlements blindly to make a load error disappear.

Sources: [Apple capture authorization](https://developer.apple.com/documentation/avfoundation/requesting-authorization-to-capture-and-save-media),
[Compose signing and notarization](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Signing_and_notarization_on_macOS/README.md).

## Verification

```shell
./gradlew :grant-desktop:jvmTest :grant-desktop:checkKotlinAbi :grant-desktop:verifyDesktopPublication
```

`verifyDesktopPublication` runs before Maven publish tasks. It rejects non-Apple-Silicon
publication hosts, an absent/empty native resource, missing generated javadoc HTML, or a
missing SBOM. Linux CI may still compile/test fallback behavior without publishing.

The JVM tests cover native status mapping, request dispatch, permission separation and
real native ABI status reads on supported hosts. They do not simulate a user's consent.
Run `bash verification/verify-desktop-preview.sh` for the complete automated staging,
consumer build and native-read smoke check (also run in macOS CI).
Follow the independent consumer's instructions to build and launch the packaged app from
staged artifacts and to exercise manual consent flows.

## Remaining gates before a public preview upload

- Record camera and microphone Allow/Don't Allow, repeat request, revocation, and relaunch
  against the packaged consumer's identity. Existing historical camera evidence alone is
  insufficient for both permissions.
- Verify the intended signing/notarization distribution mode and native library loading.
  Sandboxed distribution is not claimed by this preview.
- Audit concurrent requests, cancellation, missing callbacks and shutdown. Native consent
  currently waits for AVFoundation's callback; coroutine cancellation cannot interrupt the
  blocking native wait. Do not claim bounded request duration.
- Run full release validation for all version-aligned modules before producing the signed
  Central bundle. Staging a subset for the consumer is not a full release build.

Do not mark these gates complete based only on JVM test success. The implementation is
prepared for a preview; production Desktop support remains a separate decision.
