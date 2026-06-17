package com.browntowndev.enclave.feature.chat.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(sdk = [34], instrumentedPackages = ["androidx.loader.content"], qualifiers = "xlarge")
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

    @Test
    @DisplayName("LV-CAPTION-072")
    fun lvCaption072_longAssistantCaptionKeepsControlsVisible() {
        val longCaption =
            buildString {
                repeat(20) { index ->
                    append("This is assistant response paragraph ")
                    append(index + 1)
                    append(". ")
                }
            }

        composeTestRule.setContent {
            EnclaveTheme {
                LiveVoiceOverlay(
                    state =
                        LiveVoiceUiState.AssistantSpeaking(
                            userCaption = "Tell me a long story.",
                            assistantCaption = longCaption,
                            isMuted = false,
                            audioLevel = 0.4f,
                        ),
                    onEnd = {},
                    onToggleMute = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("End").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mute microphone").assertIsDisplayed()
        composeTestRule.onNodeWithText("Assistant speaking").assertIsDisplayed()
    }

    @Test
    @DisplayName("LV-CAPTION-073")
    fun lvCaption073_longUserCaptionShowsExpandButton() {
        val longUserCaption = buildString {
            repeat(10) { index ->
                append("The weather today is quite warm and sunny and I wanted to tell you all about it. ")
                append(index + 1)
                appendLine()
            }
        }

        composeTestRule.setContent {
            EnclaveTheme {
                LiveVoiceOverlay(
                    state =
                        LiveVoiceUiState.Listening(
                            userCaption = longUserCaption,
                            assistantCaption = "",
                            isMuted = false,
                            micLevel = 0f,
                        ),
                    onEnd = {},
                    onToggleMute = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Expand caption").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Expand caption").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Collapse caption").assertIsDisplayed()
        composeTestRule.onNodeWithText("End").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mute microphone").assertIsDisplayed()
    }

    @Test
    @DisplayName("LV-CAPTION-074")
    fun lvCaption074_shortUserCaptionHidesExpandButton() {
        composeTestRule.setContent {
            EnclaveTheme {
                LiveVoiceOverlay(
                    state =
                        LiveVoiceUiState.Listening(
                            userCaption = "Hi",
                            assistantCaption = "",
                            isMuted = false,
                            micLevel = 0f,
                        ),
                    onEnd = {},
                    onToggleMute = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Expand caption").assertDoesNotExist()
    }

    @Test
    @DisplayName("LV-CAPTION-075")
    fun lvCaption075_bothLongCaptionsKeepsControlsVisible() {
        val longCaption = buildString {
            repeat(20) { index ->
                append("This is response paragraph ")
                append(index + 1)
                append(". ")
            }
        }
        val longUserCaption = buildString {
            repeat(10) {
                append("The weather today is quite warm and sunny and I wanted to tell you all about it. ")
            }
        }

        composeTestRule.setContent {
            EnclaveTheme {
                LiveVoiceOverlay(
                    state =
                        LiveVoiceUiState.AssistantSpeaking(
                            userCaption = longUserCaption,
                            assistantCaption = longCaption,
                            isMuted = false,
                            audioLevel = 0.4f,
                        ),
                    onEnd = {},
                    onToggleMute = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("End").assertIsDisplayed()
        composeTestRule.onNodeWithText("You").assertIsDisplayed()
        composeTestRule.onNodeWithText("Assistant").assertIsDisplayed()
    }

    @Test
    @DisplayName("LV-CAPTION-076")
    fun lvCaption076_blankCaptionsShowPlaceholders() {
        composeTestRule.setContent {
            EnclaveTheme {
                LiveVoiceOverlay(
                    state =
                        LiveVoiceUiState.Listening(
                            userCaption = "",
                            assistantCaption = "",
                            isMuted = false,
                            micLevel = 0f,
                        ),
                    onEnd = {},
                    onToggleMute = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("...").assertCountEquals(2)
    }
}
