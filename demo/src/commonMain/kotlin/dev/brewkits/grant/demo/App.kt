package dev.brewkits.grant.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.brewkits.grant.grantManager
import org.koin.compose.koinInject

/**
 * Main app composable entry point
 */
@Composable
fun App() {
    // Bind GrantsController to Activity lifecycle (Android only)
    BindGrantsController()

    MaterialTheme {
        val grantManager = koinInject<grantManager>()
        val scope = rememberCoroutineScope()

        val viewModel = remember {
            GrantDemoViewModel(
                grantManager = grantManager,
                scope = scope
            )
        }

        SimpleGrantDemoScreen(viewModel = viewModel)
    }
}
