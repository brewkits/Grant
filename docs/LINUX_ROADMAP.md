# Linux Desktop roadmap

Status: planned, no Linux native permission support is shipped by the 3.0.0 preview (formerly planned as 2.7.0).
This roadmap has acceptance criteria rather than a promised release date.

## Product scope

Start with a **camera integration through XDG Desktop Portal and PipeWire** when a consumer
needs it. Keep Linux dependencies opt-in and out of Android/iOS/core. Do not advertise
generic Linux permission support or return GRANTED because a particular API is absent.

The Camera portal supports `AccessCamera` and `OpenPipeWireRemote`. After consent, the latter
returns a file descriptor used to connect the application's capture stack to PipeWire.
Consequently, a Boolean permission check alone is not a usable end-to-end integration.
Source: [XDG Camera portal](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Camera.html).

Microphone support needs a separate API/desktop-backend investigation. The Camera portal
must not be described as a general camera-and-microphone permission API. Location and
screen capture are separate future scopes, each with its own portal and session model.

## Milestones

| Phase | Deliverable | Acceptance criterion |
| --- | --- | --- |
| 0 — consumer and environment | Identify app packaging, capture library, desktop/backend versions, and camera use case | At least one consumer can describe how it will use the granted PipeWire connection |
| 1 — capability discovery | Detect session bus, Camera interface and backend availability | Missing service/interface and unavailable backend produce explicit unsupported/unavailable results |
| 2 — permission and resource ownership | Asynchronous D-Bus request, response, PipeWire FD handoff and close/cancel behavior | A packaged sample requests access, obtains a usable camera connection, and releases every FD/request on completion or cancellation |
| 3 — integration and regression coverage | JVM API adapter, sample, fake D-Bus tests and real desktop runs | Allow, deny, cancellation, backend restart, no-camera and repeated-request cases pass |
| 4 — preview publication | Opt-in artifact, sources/docs/SBOM, CI and support matrix | Independent consumer resolves the artifact and completes a real portal/capture flow in every claimed environment |

## API design constraints

- Separate unsupported backend, unspecified authorization, denied access, and successful
  resource acquisition. Preserve Grant's existing enum compatibility; do not append a new
  status as an incidental implementation shortcut.
- Treat the portal request handle and returned PipeWire FD as owned resources. Specify
  cancellation and cleanup, including cancellation before the response arrives.
- Querying support must not show a prompt or silently open a camera.
- A permission grant is scoped to the portal/capture integration. Do not infer that direct
  `/dev/video*` access or every media library now has equivalent access.
- Follow the portal's documented window identification and request-token rules. Never
  alter the permission store to manufacture consent.
- Do not put D-Bus or PipeWire binaries into `grant-core`; evaluate an optional adapter
  module after the resource handoff API is proven in a sample.

## Required test matrix

| Environment | Packaging | Evidence required |
| --- | --- | --- |
| GNOME + compatible portal backend + PipeWire | Flatpak and ordinary desktop app | Actual user consent, camera connection and cleanup |
| KDE Plasma + compatible portal backend + PipeWire | Flatpak and ordinary desktop app | Same flow, with backend-specific failure handling |
| Missing portal/backend or unavailable session bus | Ordinary app / headless CI | Explicit unavailable result, no false grant and no hang |
| No camera, busy device, revoked consent, restarted backend | Supported desktop | Distinct failure handling and resource cleanup |

Record tested distro, desktop, portal/backend, PipeWire and JVM versions. Passing JVM tests
on a headless Linux runner alone is not evidence of a real portal consent flow.

## Release decision

Do not delay the separately scoped macOS/Windows preview for this roadmap. Revisit Linux
implementation when phase 0 has a real consumer and suitable desktop test environments.
An early prototype may validate IPC and resource ownership; label it experimental until the
published environment matrix passes.
