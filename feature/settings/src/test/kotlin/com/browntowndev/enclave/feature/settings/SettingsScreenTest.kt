package com.browntowndev.enclave.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
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
            EnclaveTheme {
                SettingsScreen(
                    uiState =
                        MockSettingsData.baseUiState.copy(
                            home = MockSettingsData.baseUiState.home.copy(hapticResponse = true),
                        ),
                    onCloseClick = {},
                    onLoginClick = {},
                    onLogoutClick = {},
                    onThemeChange = {},
                    onHapticResponseChange = {},
                    onBackgroundInferenceChange = {},
                    onShowDataControlsSheet = {},
                    onAutomaticErrorTrackingChange = {},
                    onRequestDeleteAllChats = {},
                    onConfirmDeleteAllChats = {},
                    onRequestDeleteAllMedia = {},
                    onConfirmDeleteAllMedia = {},
                    onRequestDeleteAllMemories = {},
                    onConfirmDeleteAllMemories = {},
                    onRequestDeleteAllArtifacts = {},
                    onConfirmDeleteAllArtifacts = {},
                    onRequestDeleteAccount = {},
                    onConfirmDeleteAccount = {},
                    onDismissDataControlsConfirmation = {},
                    onShowMemoriesSheet = {},
                    onAddMemory = {},
                    onEditMemory = {},
                    onUpdateMemoryDraft = { _, _ -> },
                    onSaveMemory = {},
                    onCancelMemoryEdit = {},
                    onDeleteMemory = {},
                    onShowTosSheet = {},
                    onShowFeedbackSheet = {},
                    onShowUsageSpendSheet = {},
                    onUsageSpendPeriodChange = {},
                    onShowOssLicensesSheet = {},
                    onShowVisionSettingsSheet = {},
                    onNavigateToModelConfigure = {},
                    onSetDefaultModel = { _, _, _, _, _, _ -> },
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
                    onDeleteApiModelAsset = { _, _ -> },
                    onDeleteApiModelConfig = { _, _ -> },
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
                    onConfirmDeletionWithReassignment = { _, _, _ -> },
                    onDismissDeletionSafety = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Haptic Press").assertCountEquals(0)
        // The theme-family picker (ENC-9) pushes Experience below the fold; scroll to it.
        composeTestRule.onNodeWithText("Haptic Response").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNode(hasAnySibling(hasText("Haptic Response")) and isOn(), useUnmergedTree = true)
            .assertIsOn()
    }
}
