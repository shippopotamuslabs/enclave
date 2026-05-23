package com.browntowndev.enclave.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullscreenMediaDetailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fullscreenMediaActionsOverlay_rendersPromptAndNeoActionLabels() {
        composeTestRule.setContent {
            EnclaveTheme {
                FullscreenMediaActionsOverlay(
                    prompt = "Cinematic mountain lake",
                    actions =
                        listOf(
                            FullscreenMediaAction(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = {},
                            ),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText("Cinematic mountain lake").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
    }
}
