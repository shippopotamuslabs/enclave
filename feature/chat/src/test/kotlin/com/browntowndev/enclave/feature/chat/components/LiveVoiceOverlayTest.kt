package com.browntowndev.enclave.feature.chat.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import com.browntowndev.enclave.feature.chat.ToolCallBannerKind
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.DisplayName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["androidx.loader.content"])
class LiveVoiceOverlayTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @DisplayName("LV-TOOL-071")
    fun lvTool071_overlayRendersToolCallBanner() {
        var state by mutableStateOf(
            LiveVoiceUiState.Listening(
                userCaption = "",
                assistantCaption = "",
                isMuted = false,
                micLevel = 0f,
                activeToolCallBanner =
                    ToolCallBannerUi(
                        kind = ToolCallBannerKind.SEARCH,
                        label = "Searching the web...",
                    ),
            ),
        )

        composeTestRule.setContent {
            EnclaveTheme {
                LiveVoiceOverlay(
                    state = state,
                    onEnd = {},
                    onToggleMute = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Searching the web...").assertIsDisplayed()

        state =
            LiveVoiceUiState.Listening(
                userCaption = "",
                assistantCaption = "",
                isMuted = false,
                micLevel = 0f,
                activeToolCallBanner = null,
            )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Searching the web...").assertDoesNotExist()
    }
}