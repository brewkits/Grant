# Desktop preview validation — 2026-11-06

Candidate: `2.7.0-alpha01`, macOS Apple Silicon, JVM 17.
Status: local artifact preparation and automated verification passed; no Central upload.

## Completed

- All 12 library build files and the bundle script use `2.7.0-alpha01`.
- Desktop is included in the preview BOM and Central bundle module list.
- Desktop JVM publication includes sources, generated Dokka javadoc and a CycloneDX SBOM.
- Native resource inspected in the staged JVM JAR:
  `darwin-aarch64/libGrantDesktopBridge.dylib`, 881,576 bytes in this build.
- JVM SBOM includes JNA 5.19.1, coroutines JVM 1.11.0, Kotlin stdlib 2.4.10 and annotations.
- `GrantDesktop.isSupported(AppGrant)` exposes bridge/capability availability without
  conflating it with consent or physical camera/microphone availability. JVM ABI dump updated.
- Desktop tests: 9 passed, 0 failed (3 native ABI probes, 4 handler tests, 2 capability tests).
- Core JVM delegate tests: 5 passed, 0 failed.
- `:grant-desktop:checkKotlinAbi` passed after the intentional additive API update.
- `bash verification/verify-desktop-preview.sh` passed: staging, ABI/test checks, an
  independent Maven/BOM consumer, native loading and Compose `.app` creation.
- Shell syntax checks and `git diff --check` passed.

## Packaged application evidence

The separate consumer resolves Grant only from staged Maven repositories, with no project
dependencies, composite build substitution, or `mavenLocal()`. Its Grant module versions
come from the BOM. The consumer was launched via macOS `open -n`, not its raw executable.

```text
BRIDGE=LOADED
CAMERA=NOT_DETERMINED
MICROPHONE=NOT_DETERMINED
UNSUPPORTED_CONTACTS=DENIED_ALWAYS
RESULT=PASS
```

Bundle ID: `dev.brewkits.grant.desktopconsumer`. `codesign -dv` reports arm64, ad-hoc signing,
runtime flag, and no TeamIdentifier. This proves a local packaged-app load, not Developer ID
signing/notarization or App Store sandbox compatibility.

For comparison, the Gradle-run consumer returned `GRANTED` for camera and microphone from
the launching environment. Those readings are deliberately **not** counted as successful
consent requests for the packaged consumer.

## Not yet verified

- A human answering camera and microphone consent prompts (Allow and Don't Allow), repeated
  requests, revocation in Settings and relaunch behavior for the packaged consumer.
- Developer ID signed/notarized distribution and native library validation.
- Native callback cancellation/concurrency/shutdown guarantees. The existing blocking
  native request has no timeout and is not made interruptible by coroutine cancellation.
- Full release build/test/signature validation across the entire Android/iOS/Web module
  family. Only the Desktop-relevant artifacts were staged during this work.
- Windows, Linux and Intel macOS native permission support.

These remain release gates, not successful tests. No public artifact, GitHub release, or
issue-closing action was performed during this implementation.
