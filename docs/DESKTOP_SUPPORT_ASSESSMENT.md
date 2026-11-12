# Desktop support assessment — 2026-11-06

Recommendation: proceed with a narrowly scoped macOS Apple Silicon preview, after closing
the release gates below. Do not advertise general Desktop permission support yet. This is
a recommendation, not a release commitment or a claim that the gates have passed.

## Implementation follow-up

The 3.0.0-alpha01 preview (formerly planned as 2.7.0-alpha01) now has publication/BOM wiring, a capability query, handler and
capability tests, Windows x64 privacy status/Settings APIs, plus an independent staged-Maven
consumer. Native Windows CI and the packaged Windows consumer have passed. See
[validation evidence](DESKTOP_PREVIEW_VALIDATION.md) for completed checks and open manual
release gates. The analysis below records the original 2.6.0 baseline.

## Evidence and scope

Issue [#88](https://github.com/brewkits/Grant/issues/88) provides a concrete signal that at
least one consumer wants a resolvable Desktop dependency. It does not establish demand for
Windows, Linux, or every permission. Ask future adopters for OS, CPU architecture, packaging
mode, and permission requirements before widening the support matrix.

At local baseline `5b22ca1` (v2.6.0 release merge):

- Core and testing already have JVM variants. The desktop handler registry isolates the
  native bridge from Android/iOS consumers; preserve that design.
- `grant-desktop` implements camera, microphone and a Camera Settings link using JNA and
  a Kotlin/Native arm64 dylib. Its local Gradle publication is not in the Central bundle or BOM.
- `grant-compose` and `grant-core-koin` lack JVM targets. Publishing the bridge alone would
  not make the complete mobile dependency set usable from Desktop `commonMain`.
- The roadmap records a real camera Allow flow, but explicitly leaves user denial and the
  microphone consent dialog unverified. These are historical records, not tests rerun in
  this investigation.
- The native request waits on `NSCondition` without a deadline. Moving it to `Dispatchers.IO`
  prevents UI blocking but does not make a native wait cancellable. Audit concurrent requests,
  cancellation, missing callbacks, and app shutdown before promising production behavior.
- Current missing-handler fallback is `DENIED_ALWAYS`. It conflates unsupported capability
  with actual user denial; preserve compatibility for this documentation fix, but design a
  capability query before a broader Desktop launch. Adding enum values needs compatibility
  review, not an incidental patch.

## Platform decisions

| Platform | Recommendation | Reason |
| --- | --- | --- |
| macOS Apple Silicon | First preview: camera + microphone + documented Settings behavior | Existing bridge and harness make a bounded release feasible. |
| Intel macOS | Defer until an actual consumer and supported toolchain/test machine justify it | No bundled slice or verified runtime coverage today. |
| Windows | Separate feasibility spike; no blanket permission-support claim | Classic Desktop access does not map directly to mobile per-app permission prompts. |
| Linux | Defer general support; consider a portal-specific integration when demanded | Portal access can be tied to a PipeWire session/file descriptor, not just a global permission status. |

Apple requires camera/microphone usage descriptions and appropriate macOS entitlements;
the consuming app's packaging is part of correctness, not an optional installation detail.
Source: [Apple capture authorization](https://developer.apple.com/documentation/avfoundation/requesting-authorization-to-capture-and-save-media).

Microsoft documents access-denied results from camera initialization and directing users
to privacy settings. Its user guidance distinguishes Desktop apps from individually
controlled Store apps. Validate the exact Windows version and app model in a spike before
mapping these behaviors to Grant statuses.
Sources: [camera privacy API guidance](https://learn.microsoft.com/en-us/windows/apps/develop/camera/camera-privacy-setting),
[Desktop camera permissions](https://support.microsoft.com/en-au/windows/privacy/manage-app-permissions-for-a-camera-in-windows).

Linux has a real Camera portal: `AccessCamera` requests access and `OpenPipeWireRemote`
returns a file descriptor used for capture. Therefore “Linux has no permission API” is too
broad; the open question is how a portal-mediated capability fits Grant's API and consumers'
capture stacks. Source: [XDG Camera portal](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Camera.html).

## Gates for the first published preview

1. Specify the supported OS/architecture and camera/microphone scope. Keep location,
   contacts, calendar and other OSes out of the initial commitment.
2. Exercise a packaged app with its own identity: first request, Allow, Don't Allow,
   subsequent request, revocation in Settings, relaunch, and microphone independently.
   Do not count a terminal's inherited consent as evidence for the app.
3. Validate distribution: usage descriptions, signing/notarization and hardened-runtime
   library loading for the supported mode; sandbox/entitlements if claimed. Loading an
   extracted dylib during development alone does not prove a distributed app will load it.
4. Review cancellation/concurrency and missing-bridge behavior; add meaningful regression
   coverage for any behavioral changes. Clarify unsupported capabilities for application UI.
5. Add Central bundle inclusion, synchronized versioning, BOM constraint, JVM javadoc JAR,
   JVM-appropriate SBOM, signatures and metadata validation. Use the next appropriate
   minor release under the project's new-module policy, not a silent retrofit of 2.6.0.
6. Consume the staged Maven artifacts from a separate Compose Desktop application without
   project dependencies or `mavenLocal()`. Verify the JVM JAR contains the arm64 dylib, the
   dependency graph includes JNA/core, and the packaged app can load it and request consent.
7. Audit `grant-compose` and Koin for JVM portability as a separate scope. They are useful
   follow-ups, not a prerequisite for core + bridge consumers using their own UI/DI.

## Work completed for #88

The README and installation guide now link to a target matrix, `mobileMain` configuration,
and an explicitly experimental source/local-development path. JVM fallback logs no longer
tell users to install a nonexistent Central artifact. Publication and runtime semantics
remain unchanged. This addresses the issue's documented-unpublished alternative.

No native consent flow, signing/notarization run, Central upload, or external consumer
artifact-resolution test was performed as part of this documentation/log fix.
