# Android Photo Picker Integration

While `Grant` provides `AppGrant.GALLERY`, `AppGrant.GALLERY_IMAGES_ONLY`, and `AppGrant.GALLERY_VIDEO_ONLY` for traditional media permissions, modern Android development (especially Android 13+) strongly encourages the use of the **Photo Picker** instead of requesting broad read permissions.

The Photo Picker allows users to select specific media files without granting your app access to their entire gallery.

## When to use Photo Picker vs Grant
- **Use Photo Picker:** When you only need the user to pick a few photos/videos for an upload, profile picture, or message.
- **Use `AppGrant.GALLERY`:** When your app is a gallery replacement, photo editor, or backup app that needs persistent background access to all media.

## Integration Example

Because the Photo Picker relies on the `ActivityResultContracts.PickVisualMedia` contract rather than the permission system, it doesn't run through `GrantHandler`. However, you can use them together as a fallback for older devices.

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.brewkits.grant.GrantHandler

@Composable
fun MediaPickerScreen(galleryGrantHandler: GrantHandler) {
    // 1. Setup the Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // User selected an image
            processImage(uri)
        }
    }

    // 2. Function to launch the picker or fallback to permissions
    fun pickImage() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable()) {
            // Android 13+ (or backported via Google Play Services)
            // No permissions needed!
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            // Fallback for older Android versions
            galleryGrantHandler.requestSuspend(
                rationaleMessage = "We need gallery access to let you pick a photo."
            ) { status ->
                if (status == GrantStatus.GRANTED) {
                    // Launch traditional ACTION_GET_CONTENT or custom gallery UI
                    launchLegacyImagePicker()
                }
            }
        }
    }
    
    // ... UI ...
}
```
