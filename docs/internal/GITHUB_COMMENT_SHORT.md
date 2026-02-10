# GitHub Issue #1 - Short Version

---

Hi @egorikftp! 👋

Thanks for the suggestion! After careful consideration, we've decided to **keep Grant focused on permissions** and provide **documentation instead of a dedicated module**.

## Why?

- **Platform asymmetry**: Android has SettingsClient; iOS doesn't have equivalent
- **Grant's scope**: Permissions, not orchestration
- **You can build it**: Grant gives you all the primitives you need

## What We've Built (Available Now!)

All resources work with Grant `1.0.0` - no need to wait:

📚 **[Complete Recipe](https://github.com/brewkits/Grant/blob/main/docs/recipes/location-permission-with-gps-check.md)** - Basic + advanced flows, Play Services integration, testing

🎮 **[Demo App](https://github.com/brewkits/Grant/blob/main/demo/src/commonMain/kotlin/dev/brewkits/grant/demo/LocationFlowDemoScreen.kt)** - Production-ready example with full UI

💾 **[Helper Code](https://github.com/brewkits/Grant/blob/main/docs/recipes/LocationPermissionHelper.kt)** - Copy-paste solution

## Quick Example

```kotlin
suspend fun requestLocationWithGPS() {
    val status = grantManager.request(AppGrant.LOCATION)
    if (status != GrantStatus.GRANTED) return

    if (!serviceManager.isLocationEnabled()) {
        serviceManager.openLocationSettings()
        return
    }

    // Ready!
}
```

Check out the resources and let us know if you have questions!

Closing as **documented**. Thanks for the suggestion! 🙏

---

**Labels:** `documentation` `enhancement`
