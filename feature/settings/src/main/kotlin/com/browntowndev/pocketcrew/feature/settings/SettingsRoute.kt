package com.browntowndev.pocketcrew.feature.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Composable
internal fun SettingsRoute(
    onCloseClick: () -> Unit,
    onNavigateToModelConfigure: (ModelType) -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onNavigateToTtsConfigure: () -> Unit,
    onNavigateToMediaConfigure: () -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onCloseClick = onCloseClick,
        onLoginClick = viewModel::onLoginWithGoogle,
        onLogoutClick = viewModel::onLogout,
        onThemeChange = viewModel::onThemeChange,
        onHapticPressChange = viewModel::onHapticPressChange,
        onHapticResponseChange = viewModel::onHapticResponseChange,
        onBackgroundInferenceChange = viewModel::onBackgroundInferenceChange,
        onShowDataControlsSheet = viewModel::onShowDataControlsSheet,
        onShowMemoriesSheet = viewModel::onShowMemoriesSheet,
        onAddMemory = viewModel::onAddMemory,
        onEditMemory = viewModel::onEditMemory,
        onUpdateMemoryDraft = viewModel::onUpdateMemoryDraft,
        onSaveMemory = viewModel::onSaveMemory,
        onCancelMemoryEdit = viewModel::onCancelMemoryEdit,
        onDeleteMemory = viewModel::onDeleteMemory,
        onShowTosSheet = viewModel::onShowTosSheet,
        onShowFeedbackSheet = viewModel::onShowFeedbackSheet,
        onShowOssLicensesSheet = viewModel::onShowOssLicensesSheet,
        onShowVisionSettingsSheet = viewModel::onShowVisionSettingsSheet,
        onNavigateToModelConfigure = onNavigateToModelConfigure,
        onSetDefaultModel = viewModel::onSetDefaultModel,
        onShowLocalModelsSheet = viewModel::onShowModelConfigSheet,
        onShowByokSheet = viewModel::onShowByokSheet,
        onNavigateToByokConfigure = onNavigateToByokConfigure,
        onStartCreateApiModelAsset = viewModel::onStartCreateApiModelAsset,
        onShowSearchSkillSheet = viewModel::onShowSearchSkillSheet,
        onSearchSkillTabChange = viewModel::onSearchSkillTabChange,
        onSearchEnabledChange = viewModel::onSearchEnabledChange,
        onSearchProviderChange = viewModel::onSearchProviderChange,
        onTavilyApiKeyChange = viewModel::onTavilyApiKeyChange,
        onClearTavilyApiKey = viewModel::onClearTavilyApiKey,
        onExaApiKeyChange = viewModel::onExaApiKeyChange,
        onClearExaApiKey = viewModel::onClearExaApiKey,
        onUpdateShowSearchProviderSwitchAlert = viewModel::onUpdateShowSearchProviderSwitchAlert,
        onSaveSearchSkillSettings = viewModel::onSaveSearchSkillSettings,
        onXSearchEnabledChange = viewModel::onXSearchEnabledChange,
        onXApiKeyChange = viewModel::onXApiKeyChange,
        onClearXApiKey = viewModel::onClearXApiKey,
        onSaveXSearchSettings = viewModel::onSaveXSearchSettings,
        onSelectApiModelAsset = viewModel::onSelectApiModelAsset,
        onSelectApiModelConfig = viewModel::onSelectApiModelConfig,
        onDeleteApiModelAsset = viewModel::onDeleteApiModelAsset,
        onDeleteApiModelConfig = { id -> viewModel.onDeleteApiModelConfig(id, {}) },
        onShowTtsProvidersSheet = viewModel::onShowTtsProvidersSheet,
        onNavigateToTtsConfigure = onNavigateToTtsConfigure,
        onStartCreateTtsProviderAsset = viewModel::onStartCreateTtsProviderAsset,
        onSelectTtsProviderAsset = viewModel::onSelectTtsProviderAsset,
        onDeleteTtsProviderAsset = viewModel::onDeleteTtsProviderAsset,
        onShowMediaProvidersSheet = viewModel::onShowMediaProvidersSheet,
        onNavigateToMediaConfigure = onNavigateToMediaConfigure,
        onStartCreateMediaProviderAsset = viewModel::onStartCreateMediaProviderAsset,
        onSelectMediaProviderAsset = viewModel::onSelectMediaProviderAsset,
        onDeleteMediaProviderAsset = viewModel::onDeleteMediaProviderAsset,
        onNavigateToLocalModelConfigure = onNavigateToLocalModelConfigure,
        onSelectLocalModelAsset = viewModel::onSelectLocalModelAsset,
        onSelectLocalModelConfig = viewModel::onSelectLocalModelConfig,
        onDeleteLocalModelAsset = viewModel::onDeleteLocalModelAsset,
        onDeleteLocalModelConfig = { id -> viewModel.onDeleteLocalModelConfig(id, {}) },
        onConfirmDeletionWithReassignment = viewModel::onConfirmDeletionWithReassignment,
        onDismissDeletionSafety = viewModel::onDismissDeletionSafety,
        onSubmitVaultReauthPassword = viewModel::onSubmitVaultReauthPassword,
        onConfirmAdoptCloudFromSettings = viewModel::onConfirmAdoptCloudFromSettings,
        onCancelVaultReauth = viewModel::onCancelVaultReauth,
    )
}
