# Desktop preview validation — 2026-11-06

Candidate: `2.7.0-alpha01`, macOS Apple Silicon + Windows x64, JVM 17.
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
- Desktop tests: 15 passed on Windows CI (3 macOS/fallback probes, 4 macOS handler tests,
  2 capability tests, 5 Windows mapping tests, 1 real Windows native ABI/access probe).
- Core JVM delegate tests: 5 passed, 0 failed.
- `:grant-desktop:checkKotlinAbi` passed after the intentional additive API update.
- `bash verification/verify-desktop-preview.sh` passed: staging, ABI/test checks, an
  independent Maven/BOM consumer, native loading and Compose `.app` creation.
- Full repository `./gradlew build allTests checkKotlinAbi` completed successfully in 10m 51s
  (1,654 actionable tasks: 543 executed, 1,111 up-to-date).
- The first full run exposed Android Lint's metadata mismatch (2.4.0 versus 2.2.0). Fixed by
  pinning `android.experimental.lint.version=9.5.1`, without changing AGP. All 11 Android
  modules then passed `lint` with 0 errors (68 warnings remain); no metadata incompatibility
  diagnostics remained. A forced `:grant-core:lintDebug` rerun also passed under JDK 17.
  [Android documents this independent Lint upgrade mechanism](https://developer.android.com/jetpack/androidx/releases/compose-runtime).
- Publication's negative check passed: omitting the Windows input directory is rejected even
  after a successful staged build. The positive check validates the bundled DLL against the
  explicitly selected CI input.
- Shell syntax checks and `git diff --check` passed.

## Windows native and packaged-consumer evidence

[Desktop CI run 42240297371](https://github.com/brewkits/Grant/actions/runs/42240297371)
passed on source commit `414d9a3`: Windows native DLL/tests, macOS universal-JAR staging,
and a separate Windows Maven consumer built as an `.exe`. No project dependencies or
`mavenLocal()` were used by the consumer. C++20 is required by the current MSVC/Windows SDK.

The packaged Windows consumer reported:

```text
WINDOWS_CAMERA=DENIED_BY_USER
WINDOWS_MICROPHONE=DENIED_BY_USER
NATIVE_PERMISSION_REQUESTS=UNSUPPORTED
BRIDGE=LOADED
CAMERA=DENIED_ALWAYS
MICROPHONE=DENIED_ALWAYS
UNSUPPORTED_CONTACTS=DENIED_ALWAYS
RESULT=PASS
```

The `WINDOWS_*` values come from the in-process WinRT API. The separate `CAMERA` and
`MICROPHONE` lines are GrantManager's unchanged unsupported-request fallback on Windows;
no Windows permission handlers are registered. This verifies truthful status mapping and
packaged native loading. It does not prove interactive Settings navigation or privacy-toggle
transitions. The runner's existing denial is not a manually exercised user-denial flow.

## Packaged macOS application evidence

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
- Signed release-bundle/signature validation. Full local build/tests/ABI and the corrected
  Android Lint checks passed. Only Desktop-relevant Maven artifacts were staged; no complete
  signed release bundle was produced.
- Interactive Windows Settings/toggle changes, Windows ARM64 JVM, Linux and Intel macOS support.

These remain release gates, not successful tests. No public artifact, GitHub release, or
issue-closing action was performed during this implementation.
