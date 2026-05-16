package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun experienceSection_removesHapticPressAndKeepsHapticResponse() {
        composeTestRule.setContent {
            PocketCrewTheme {
                SettingsScreen(
                    uiState = MockSettingsData.baseUiState.copy(
                        home = MockSettingsData.baseUiState.home.copy(hapticResponse = true),
                    ),
                    onCloseClick = {},
                    onLoginClick = {},
                    onLogoutClick = {},
                    onThemeChange = {},
                    onHapticResponseChange = {},
                    onBackgroundInferenceChange = {},
                    onShowDataControlsSheet = {},
                    onShowMemoriesSheet = {},
                    onAddMemory = {},
                    onEditMemory = {},
                    onUpdateMemoryDraft = { _, _ -> },
                    onSaveMemory = {},
                    onCancelMemoryEdit = {},
                    onDeleteMemory = {},
                    onOpenToS = {},
                    onShowFeedbackSheet = {},
                    onShowVisionSettingsSheet = {},
                    onNavigateToModelConfigure = {},
                    onSetDefaultModel = { _, _, _, _, _ -> },
                    onShowLocalModelsSheet = {},
                    onShowByokSheet = {},
                    onNavigateToByokConfigure = {},
                    onStartCreateApiModelAsset = {},
                    onShowSearchSkillSheet = {},
                    onSearchSkillTabChange = {},
                    onSearchEnabledChange = {},
                    onSearchProviderChange = {},
                    onTavilyApiKeyChange = {},
                    onClearTavilyApiKey = {},
                    onExaApiKeyChange = {},
                    onClearExaApiKey = {},
                    onUpdateShowSearchProviderSwitchAlert = {},
                    onSaveSearchSkillSettings = { onSuccess -> onSuccess() },
                    onXSearchEnabledChange = {},
                    onXApiKeyChange = {},
                    onClearXApiKey = {},
                    onSaveXSearchSettings = { onSuccess -> onSuccess() },
                    onSelectApiModelAsset = {},
                    onSelectApiModelConfig = {},
                    onDeleteApiModelAsset = {},
                    onDeleteApiModelConfig = {},
                    onShowTtsProvidersSheet = {},
                    onNavigateToTtsConfigure = {},
                    onStartCreateTtsProviderAsset = {},
                    onSelectTtsProviderAsset = {},
                    onDeleteTtsProviderAsset = {},
                    onShowMediaProvidersSheet = {},
                    onNavigateToMediaConfigure = {},
                    onStartCreateMediaProviderAsset = {},
                    onSelectMediaProviderAsset = {},
                    onDeleteMediaProviderAsset = {},
                    onNavigateToLocalModelConfigure = {},
                    onSelectLocalModelAsset = {},
                    onSelectLocalModelConfig = {},
                    onDeleteLocalModelAsset = {},
                    onDeleteLocalModelConfig = {},
                    onConfirmDeletionWithReassignment = { _, _ -> },
                    onDismissDeletionSafety = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Haptic Press").assertCountEquals(0)
        composeTestRule.onNodeWithText("Haptic Response").assertIsDisplayed()
        composeTestRule
            .onNode(hasAnySibling(hasText("Haptic Response")) and isOn(), useUnmergedTree = true)
            .assertIsOn()
    }
}
