package dev.brewkits.grant.demo

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantManager
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicReference

/**
 * Wires the Compose-managed [ActivityResultLauncher] into [GrantManager] on Android.
 *
 * [rememberLauncherForActivityResult] must be called during composition (safe from Compose),
 * so this is the correct place to register the permission launcher rather than in Activity.onCreate.
 *
 * The [AtomicReference] holds the per-call callback set by [GrantLauncher.launch] and consumed
 * when the system dialog returns, preventing the callback from being captured at registration time.
 */
@Composable
actual fun BindGrantsController() {
    val grantManager = koinInject<GrantManager>()
    val pendingCallback = remember { AtomicReference<((Map<String, Boolean>) -> Unit)?>(null) }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        pendingCallback.getAndSet(null)?.invoke(results)
    }

    SideEffect {
        grantManager.setLauncher(object : GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                pendingCallback.set(onResult)
                activityLauncher.launch(permissions.toTypedArray())
            }
        })
    }
}
