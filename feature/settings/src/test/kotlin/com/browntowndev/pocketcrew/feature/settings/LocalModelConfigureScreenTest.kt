package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalModelConfigureScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saveButton_isVisible_forEditablePreset() {
        val uiState = SettingsUiState(
            localModelEditor = LocalModelEditorUiState(
                configDraft = LocalModelConfigUi(
                    isSystemPreset = false,
                    displayName = "Custom Preset"
                )
            )
        )

        composeTestRule.setContent {
            PocketCrewTheme {
                LocalModelConfigureScreen(
                    uiState = uiState,
                    modelFormat = "GGUF",
                    onNavigateBack = {},
                    onConfigChange = {},
                    onSave = {}
                )
            }
        }

        // Using a test tag that will be added in implementation
        composeTestRule.onNodeWithTag("SaveButton").assertIsDisplayed()
    }

    @Test
    fun saveButton_isHidden_forSystemPreset() {
        val uiState = SettingsUiState(
            localModelEditor = LocalModelEditorUiState(
                configDraft = LocalModelConfigUi(
                    isSystemPreset = true,
                    displayName = "System Preset"
                )
            )
        )

        composeTestRule.setContent {
            PocketCrewTheme {
                LocalModelConfigureScreen(
                    uiState = uiState,
                    modelFormat = "GGUF",
                    onNavigateBack = {},
                    onConfigChange = {},
                    onSave = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveButton").assertDoesNotExist()
    }

    @Test
    fun saveButton_isEnabled_withValidData() {
        val uiState = SettingsUiState(
            localModelEditor = LocalModelEditorUiState(
                configDraft = LocalModelConfigUi(
                    isSystemPreset = false,
                    displayName = "Valid Name",
                    contextWindow = "4096"
                )
            )
        )

        composeTestRule.setContent {
            PocketCrewTheme {
                LocalModelConfigureScreen(
                    uiState = uiState,
                    modelFormat = "GGUF",
                    onNavigateBack = {},
                    onConfigChange = {},
                    onSave = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveButton").assertIsEnabled()
    }

    @Test
    fun saveButton_isDisabled_withEmptyName() {
        val uiState = SettingsUiState(
            localModelEditor = LocalModelEditorUiState(
                configDraft = LocalModelConfigUi(
                    isSystemPreset = false,
                    displayName = "",
                    contextWindow = "4096"
                )
            )
        )

        composeTestRule.setContent {
            PocketCrewTheme {
                LocalModelConfigureScreen(
                    uiState = uiState,
                    modelFormat = "GGUF",
                    onNavigateBack = {},
                    onConfigChange = {},
                    onSave = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveButton").assertIsNotEnabled()
    }

    @Test
    fun saveButton_isDisabled_withEmptyContextWindow() {
        val uiState = SettingsUiState(
            localModelEditor = LocalModelEditorUiState(
                configDraft = LocalModelConfigUi(
                    isSystemPreset = false,
                    displayName = "Valid Name",
                    contextWindow = ""
                )
            )
        )

        composeTestRule.setContent {
            PocketCrewTheme {
                LocalModelConfigureScreen(
                    uiState = uiState,
                    modelFormat = "GGUF",
                    onNavigateBack = {},
                    onConfigChange = {},
                    onSave = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveButton").assertIsNotEnabled()
    }
}
