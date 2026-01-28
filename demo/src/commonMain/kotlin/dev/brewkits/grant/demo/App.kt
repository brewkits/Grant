package dev.brewkits.grant.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.brewkits.grant.GrantManager
import org.koin.compose.koinInject

/**
 * Main app composable entry point
 */
@Composable
fun App() {
    // Bind GrantsController to Activity lifecycle (Android only)
    BindGrantsController()

    MaterialTheme {
        val grantManager = koinInject<GrantManager>()
        val scope = rememberCoroutineScope()

        val viewModel = remember {
            GrantDemoViewModel(
                grantManager = grantManager,
                scope = scope
            )
        }

        var showDemo by remember { mutableStateOf(false) }

        if (showDemo) {
            SimpleGrantDemoScreen(viewModel = viewModel)
        } else {
            DemoApp(onStartDemo = { showDemo = true })
        }
    }
}
