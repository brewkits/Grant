# Support policy

What you can rely on when you put Grant in a production app.

## Versioning

Grant follows semantic versioning.

| Change | Version bump |
|---|---|
| Breaking public API change | major |
| New permission, new API, new module | minor |
| Bug fix, docs, dependency bump | patch |

The public API surface is not a matter of opinion: every published module commits an ABI
dump under `<module>/api/`, and CI fails on any undeclared change to it. A release cannot be
bundled with an unreviewed API change — `create-grant-maven-bundle-auto.sh` runs the same
check. See `CLAUDE.md` for the workflow.

## Supported versions

The **latest minor of the current major** receives fixes. When a new major ships, the
previous major receives security fixes for **six months**.

Security issues: see [SECURITY.md](SECURITY.md). Please do not report them in public issues.

## Platform support

| | Supported |
|---|---|
| Android | `minSdk 26` (Android 8.0) — `compileSdk 36`, with Android 17 (API 37) behaviour handled |
| iOS | arm64, simulator arm64, x64 — except `grant-compose`, which dropped iosX64 in 2.3.0 because Compose Multiplatform 1.11 stopped publishing that target |
| Kotlin | 2.4.x |
| JVM target | 17 |

**`minSdk` is raised only in a major release**, and only when the lowest supported level is
below roughly 1% of active devices. A `minSdk` bump breaks consuming apps that support older
devices, so it is treated as a breaking change, not housekeeping.

**New Android and iOS releases** are supported in a minor version once their permission
behaviour is understood and tested — not on the day the beta drops. Grant's value is in the
edge cases, and shipping an untested guess about a new OS would work against that.

## What Grant will not do

Stated so it can be relied on:

- **No permissions are added to your app.** `grant-core` declares zero `<uses-permission>`
  entries; nothing appears on your Play listing that you did not ask for. If you use
  `ServiceType.WIFI`, you declare `ACCESS_WIFI_STATE` yourself — see the installation guide.
- **No data collection, no network calls, no analytics.** Grant talks to the OS permission
  APIs and nothing else.
- **No logging unless you ask for it.** `GrantLogger.isEnabled` defaults to `false` with no
  handler installed. Only permission identifiers and flow state are ever logged — never the
  contents of a permission.
- **No review-circumvention techniques.** An obfuscated `requestAlwaysAuthorization` call was
  proposed and rejected in v2.2.0 as an App Store Guideline 2.3.1 risk; module isolation is
  the transparent fix. See `docs/ios/APPLE_FRAMEWORK_LINKING_ISSUE.md`.

## Reporting a problem

Open an issue with the platform, OS version, Grant version, and the permission involved.
Permission bugs are usually specific to an API level or an OS dialog variant, and those
three facts are what make one reproducible.
