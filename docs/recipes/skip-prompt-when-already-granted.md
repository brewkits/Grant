# Skip the prompt when a permission is already granted

A common screen — e.g. a QR scanner — needs a permission (camera) to work. You want:

- **First launch / not granted** → show an explainer + a button, so the user understands *why* before the system dialog appears. Then `GrantHandler` drives rationale → settings-guide on denials.
- **Already granted in a previous session** → go **straight to the feature**, with no button and no prompt.

You do **not** need to call `request()` in your ViewModel's `init {}` (that would skip the explainer on first launch). Instead, observe the status `GrantHandler` already exposes.

## How it works

`GrantHandler` populates a `status: StateFlow<GrantStatus>` in its `init {}` by calling `checkStatus()`. `checkStatus()` is a **pure read of the OS state — it never shows a system dialog**. So observing `handler.status` is side-effect free: it tells you GRANTED / DENIED / DENIED_ALWAYS / NOT_DETERMINED without prompting.

## ViewModel

```kotlin
class QrViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraHandler = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope,
    )

    // Already populated by checkStatus() in init{} — no prompt is shown.
    val cameraStatus: StateFlow<GrantStatus> = cameraHandler.status
}
```

## UI

```kotlin
@Composable
fun QrRoute(vm: QrViewModel) {
    val status by vm.cameraStatus.collectAsState()

    when (status) {
        GrantStatus.GRANTED,
        GrantStatus.PARTIAL_GRANTED -> QrScanner()      // already granted → straight to the feature

        else -> PermissionPrompt(                         // NOT_DETERMINED / DENIED / DENIED_ALWAYS → explainer + button
            onRequestClick = {
                vm.cameraHandler.request(
                    rationaleMessage = "QR scanning needs the camera.",
                ) { /* onGranted — the status flow flips the UI to QrScanner() */ }
            },
        )
    }

    // Drives rationale → settings-guide dialogs on denials
    GrantDialog(vm.cameraHandler)
}
```

This yields exactly the desired behaviour:

| Situation | `status` after `init{}` | What the user sees |
|---|---|---|
| First launch | `NOT_DETERMINED` | Explainer + button; system dialog only after the button (with your context shown first) |
| Already granted last session | `GRANTED` | Goes straight to the scanner; no button, no prompt |
| Previously denied, app restarted | `DENIED` / `DENIED_ALWAYS` | Explainer + button; tapping it shows rationale or routes to Settings |

## `status` vs the `onGranted` callback — when to use which

`GrantHandler` gives you two ways to react to a grant, and they are **not** interchangeable. Picking the wrong one leads to either a UI that doesn't update or a side effect that fires too many times.

| | `status: StateFlow<GrantStatus>` | `onGranted` callback (in `request {}`) |
|---|---|---|
| Trigger model | **Level-triggered** — always holds the current status and re-emits it on every collection | **Edge-triggered** — fires *exactly once* at the moment the permission becomes granted, then is cleared (to avoid leaking the lambda) |
| Survives recomposition / restart | Yes — it is durable state you observe | No — it is a one-shot event |
| Use it for | **Declarative UI**: "should I render the scanner or the button?" | **One-shot imperative side effects** that should run *once* when the user grants |

**Rule of thumb:**

- Use **`status`** to decide *what to show*. This is the right tool for the recipe above — rendering the scanner is declarative UI state, so it stays correct across recomposition and process restart. For this case the `onGranted` lambda is redundant; an empty `{ }` is fine.
- Use **`onGranted`** for an action you want to fire *once* the moment the user grants — for example navigate to the next screen, start a single download, capture one frame, or send the location once. It also fires on the **return-from-Settings** path (via `refreshStatus()`), not only on the in-dialog grant.

> ⚠️ Don't drive one-shot actions off the `status` flow. Because `status` re-emits on every recomposition, an action like "navigate" or "start download" placed in your `when (status) { GRANTED -> ... }` branch would run repeatedly. Put those in `onGranted` instead.

```kotlin
// status → declarative UI (render scanner). onGranted → one-shot side effect (navigate once).
vm.cameraHandler.request(
    rationaleMessage = "QR scanning needs the camera.",
) {
    // Runs once, at the moment access is granted (incl. coming back from Settings).
    navController.navigate("scanner")
}
```

## Returning from Settings

When the user leaves to the system Settings and comes back, call `handler.refreshStatus()` (e.g. on `Lifecycle.Event.ON_RESUME`) so the flow re-reads the OS state and your `when` switches to the feature if they enabled it there.

## See also

- [GrantStore Architecture](../architecture/grant-store.md) — why a permanently-denied permission still reports `DENIED_ALWAYS` (and routes to Settings) on the first tap after a cold restart.
