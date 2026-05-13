package dev.brewkits.grant.impl

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dev.brewkits.grant.GrantLauncher

/**
 * Android implementation of [GrantLauncher] using [ActivityResultLauncher].
 *
 * Must be created via [from] before the Activity or Fragment reaches STARTED state
 * (i.e., as a property or in `onCreate`).
 *
 * The per-call [onResult] passed to [launch] is stored and invoked when the system
 * permission dialog returns — the registered [ActivityResultLauncher] callback routes
 * the result back to it.
 */
class AndroidGrantLauncher private constructor() : GrantLauncher {

    @Volatile private var pendingCallback: ((Map<String, Boolean>) -> Unit)? = null
    private lateinit var launcher: ActivityResultLauncher<Array<String>>

    override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
        pendingCallback = onResult
        launcher.launch(permissions.toTypedArray())
    }

    companion object {
        /**
         * Creates an [AndroidGrantLauncher] from an Activity.
         *
         * Call this during `onCreate` or as a property initializer, before the Activity is STARTED.
         */
        fun from(activity: ComponentActivity): AndroidGrantLauncher {
            val grantLauncher = AndroidGrantLauncher()
            grantLauncher.launcher = activity.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                grantLauncher.pendingCallback?.invoke(results)
                grantLauncher.pendingCallback = null
            }
            return grantLauncher
        }

        /**
         * Creates an [AndroidGrantLauncher] from a Fragment.
         *
         * Call this during `onCreate` or as a property initializer, before the Fragment is STARTED.
         */
        fun from(fragment: Fragment): AndroidGrantLauncher {
            val grantLauncher = AndroidGrantLauncher()
            grantLauncher.launcher = fragment.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                grantLauncher.pendingCallback?.invoke(results)
                grantLauncher.pendingCallback = null
            }
            return grantLauncher
        }
    }
}
