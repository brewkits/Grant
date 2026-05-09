# UsbManager Permission Support

While Android's `UsbManager` requires user permission to access USB devices, it does not use the standard `checkSelfPermission` / `requestPermissions` flow. Instead, it uses `PendingIntent` and `UsbManager.requestPermission()`.

Because this falls outside the standard Android permission framework, it cannot be directly requested via `AppGrant`. However, you can wrap the logic in a custom class and track the state using `GrantStore` and `RawPermission` to provide a consistent UI experience.

## Example: Tracking USB Permissions with Grant

By utilizing `RawPermission`, you can still track whether the user has permanently denied USB access and show the appropriate Rationale or Settings dialog using `GrantHandler`.

```kotlin
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.RawPermission

object CustomGrants {
    val USB_ACCESS = RawPermission("dev.mycompany.usb_access")
}

class UsbConnectionManager(
    private val grantManager: GrantManager,
    private val usbManager: UsbManager,
    private val context: Context
) {
    // 1. Setup the handler with the RawPermission
    val usbGrantHandler = GrantHandler(
        grantManager = grantManager,
        grant = CustomGrants.USB_ACCESS,
        scope = coroutineScope
    )
    
    fun connectToDevice(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            // Already has permission
            openDevice(device)
            return
        }
        
        // Use requestWithCustomUi since the OS dialogue is triggered by PendingIntent,
        // not by GrantManager natively.
        usbGrantHandler.requestWithCustomUi(
            rationaleMessage = "We need USB access to communicate with the connected hardware.",
            settingsMessage = "Please reconnect the device and grant access.",
            onShowRationale = { msg, onConfirm, _ ->
                // Show your custom rationale UI...
                onConfirm()
            },
            onShowSettings = { msg, onConfirm, _ ->
                // Show instructions...
                onConfirm()
            }
        ) {
            // Trigger actual USB permission request
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), 
                PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            
            // Mark the raw permission as requested in the GrantStore
            grantManager.store.markRawPermissionRequested(CustomGrants.USB_ACCESS.identifier)
        }
    }
}
```

This pattern unifies your permission tracking logic under the Grant ecosystem, ensuring your analytics, UI handling, and state logging remain consistent regardless of the underlying Android API.