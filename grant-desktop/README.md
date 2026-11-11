# Grant Desktop preview

**Development version: 3.0.0-alpha01 (formerly planned as 2.7.0-alpha01). Not yet uploaded to Maven Central.**
The 2.6.0 release does not publish `grant-desktop`. This preview adds macOS permission
handlers, Windows x64 privacy checks/settings, and publication,
BOM constraint, native packaging guard, generated JVM javadoc and JVM dependency SBOM.

## Scope

| Capability | Preview implementation |
| --- | --- |
| JVM | Java 17; macOS Apple Silicon or Windows x64 |
| Camera / microphone | AVFoundation authorization status and native consent request |
| Settings | `GrantDesktop.openSettings(grant)` opens Camera or Microphone privacy settings |
| Other permissions | No built-in desktop handlers; core returns `DENIED_ALWAYS` |
| Windows x64 | Device-class privacy status reads and camera/microphone Settings; no native permission requests |
| Intel macOS / Windows ARM64 JVM / Linux | No native bridge bundled |

This is a core/bridge preview. `grant-compose` and `grant-core-koin` do not have JVM
variants; supply your own Desktop UI and DI. JNA is confined to the Desktop module.
The bridge does not capture audio/video or add permission declarations to the host app.

## Windows privacy access

On Windows x64, call `GrantDesktop.checkWindowsPrivacyStatus(AppGrant.CAMERA)` or
`AppGrant.MICROPHONE` from a background thread. The in-process WinRT
`DeviceAccessInformation` API reports `ALLOWED`, `DENIED_BY_USER`, `DENIED_BY_SYSTEM`, or
`UNSPECIFIED`. Missing native support, failed queries and unsupported permissions return
`UNAVAILABLE`. Unspecified/unavailable must never be treated as permission granted.

This is device-class access information, not a capture attempt or a guarantee that a
camera/microphone is present, idle, or usable by a particular media API. Packaged/MSIX and
ordinary full-trust apps can have different access policies. Applications must still handle
capture errors and test their actual packaging mode.

`GrantDesktop.openSettings(AppGrant.CAMERA)` and `.openSettings(AppGrant.MICROPHONE)` open
the documented Windows privacy pages. `GrantDesktop.initialize()` also routes
`GrantManager.openSettings(grant)` to the page matching that grant (Microphone opens the
Microphone page; Camera and any other grant open the Camera page), and the parameterless
`GrantManager.openSettings()` to the Camera page. A successful launch does not mean the user
changed privacy settings.

After `GrantDesktop.initialize()`, Windows camera/microphone state is also available through
`GrantManager`, **read-only**: `checkStatus()` maps `ALLOWED` to `GRANTED`, `DENIED_BY_USER` and
`DENIED_BY_SYSTEM` to `DENIED_ALWAYS`, `UNSPECIFIED` to `NOT_DETERMINED`, and an unavailable
bridge to `DENIED_ALWAYS` with a log. `GrantManager.request()` cannot prompt on Windows, so it
returns that same state; direct the user to Settings instead. `GrantDesktop.isSupported(...)`
checks native **permission-request** support and remains false on Windows. The preview does not
manufacture a user grant or attempt to show a WinRT consent dialog from a JVM worker thread.

Sources: [DeviceAccessInformation](https://learn.microsoft.com/en-us/uwp/api/windows.devices.enumeration.deviceaccessinformation),
[DeviceAccessStatus](https://learn.microsoft.com/en-us/uwp/api/windows.devices.enumeration.deviceaccessstatus),
[Windows privacy settings URIs](https://learn.microsoft.com/en-us/windows/apps/develop/launch/launch-settings).

## Dependency and initialization

After publishing the preview to a repository available to your app:

```kotlin
kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(platform("dev.brewkits:grant-bom:3.0.0-alpha01"))
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

## macOS packaging and consent

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
./gradlew -PgrantWindowsBridgeDir=/path/to/tested-windows-artifact \
  :grant-desktop:jvmTest :grant-desktop:checkKotlinAbi :grant-desktop:verifyDesktopPublication
```

`verifyDesktopPublication` runs before Maven publish tasks. It rejects non-Apple-Silicon
publication hosts, an absent/empty native resource, missing generated javadoc HTML, or a
missing SBOM. It requires both the macOS dylib and Windows x64 DLL. The Windows DLL is
built using CMake, MSVC with C++20, and the Windows SDK; import the artifact from the same
commit's successful Windows CI job. The CI artifact also carries `bridge-source-revision.txt`
(the output of `git rev-parse HEAD` when the DLL was built); `verifyDesktopPublication`
refuses to publish if it is missing or differs from the checked-out commit, so a DLL from an
earlier or different commit cannot be bundled by accident. (A DLL built by hand needs that
file written the same way.) Linux CI can compile/test fallback behavior without
publishing. The root bundle script accepts the directory as `GRANT_WINDOWS_BRIDGE_DIR`
and preserves both files across its clean build.

The JVM tests cover native status mapping, request dispatch, permission separation and
real native ABI status reads on supported hosts. They do not simulate a user's consent.
Run `GRANT_WINDOWS_BRIDGE_DIR=<downloaded-artifact-directory> bash verification/verify-desktop-preview.sh` for the complete automated staging,
consumer build and native-read smoke check. `desktop.yml` builds/tests the DLL on Windows,
stages the universal JVM artifact on macOS, then runs a separate packaged Windows consumer.
Follow the independent consumer's instructions to build and launch the packaged app from
staged artifacts and to exercise manual consent flows.

## Demo and Linux

Launch the independent consumer with `--demo` for a Compose UI. Windows shows privacy
status/Settings controls; macOS additionally offers native permission requests. Refresh
status after changing access in Settings. See the [consumer guide](../verification/desktop-consumer/README.md).
Linux implementation is deferred; see the [Linux roadmap](../docs/LINUX_ROADMAP.md).

## Remaining gates before a public preview upload

- On Windows, verify privacy-toggle changes and both Settings buttons in an interactive
  Windows session with the intended packaging mode; headless CI does not prove this UX.
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
