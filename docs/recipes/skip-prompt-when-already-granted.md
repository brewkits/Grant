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

## Returning from Settings

When the user leaves to the system Settings and comes back, call `handler.refreshStatus()` (e.g. on `Lifecycle.Event.ON_RESUME`) so the flow re-reads the OS state and your `when` switches to the feature if they enabled it there.

## See also

- [GrantStore Architecture](../architecture/grant-store.md) — why a permanently-denied permission still reports `DENIED_ALWAYS` (and routes to Settings) on the first tap after a cold restart.
