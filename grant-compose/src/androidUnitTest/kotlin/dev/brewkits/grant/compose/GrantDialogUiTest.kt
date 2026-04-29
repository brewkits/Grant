package dev.brewkits.grant.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI verification tests for GrantDialog using Robolectric and Compose Test Rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class GrantDialogUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val mockGrantManager = FakeGrantManager()

    @Test
    fun rationaleDialog_displaysCorrectText_andHandlesConfirm() {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // Trigger Rationale state
        mockGrantManager.mockStatus = GrantStatus.DENIED
        handler.request(rationaleMessage = "Camera is needed") { }
        handler.request { } // Second call to trigger rationale
        
        composeTestRule.setContent {
            MaterialTheme {
                GrantDialog(handler = handler)
            }
        }

        // Verify Title and Message
        composeTestRule.onNodeWithText("Permission Required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camera is needed").assertIsDisplayed()

        // Verify Button and Interaction
        composeTestRule.onNodeWithText("Continue").performClick()
        
        // Confirm logic was triggered
        assert(mockGrantManager.requestCalled)
    }

    @Test
    fun settingsDialog_displaysCorrectText_andHandlesConfirm() {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // Trigger Settings state
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        handler.request(settingsMessage = "Go to Settings now") { }
        
        composeTestRule.setContent {
            MaterialTheme {
                GrantDialog(handler = handler)
            }
        }

        // Verify Text
        composeTestRule.onNodeWithText("Permission Denied").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go to Settings now").assertIsDisplayed()

        // Verify Interaction
        composeTestRule.onNodeWithText("Open Settings").performClick()
        assert(mockGrantManager.openSettingsCalled)
    }

    @Test
    fun dialog_isRemoved_whenDismissed() {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        handler.request { }
        
        composeTestRule.setContent {
            MaterialTheme {
                GrantDialog(handler = handler)
            }
        }

        composeTestRule.onNodeWithText("Permission Denied").assertIsDisplayed()

        // Dismiss
        composeTestRule.onNodeWithText("Cancel").performClick()
        
        // Verify dialog is gone from tree
        composeTestRule.onNodeWithText("Permission Denied").assertDoesNotExist()
    }
}
