package com.browntowndev.pocketcrew.presentation.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    onNavigateToModelDownload: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreen(
        uiState = uiState,
        onCloseClick = onNavigateBack,
        onThemeChange = viewModel::onThemeChange,
        onHapticPressChange = viewModel::onHapticPressChange,
        onHapticResponseChange = viewModel::onHapticResponseChange,
        onShowCustomizationSheet = viewModel::onShowCustomizationSheet,
        onCustomizationEnabledChange = viewModel::onCustomizationEnabledChange,
        onPromptOptionChange = viewModel::onPromptOptionChange,
        onCustomPromptTextChange = viewModel::onCustomPromptTextChange,
        onSaveCustomization = viewModel::onSaveCustomization,
        onShowDataControlsSheet = viewModel::onShowDataControlsSheet,
        onAllowMemoriesChange = viewModel::onAllowMemoriesChange,
        onDeleteAllConversations = {
            viewModel.onDeleteAllConversations()
            onShowSnackbar("All conversations deleted", null)
        },
        onDeleteAllMemories = {
            viewModel.onDeleteAllMemories()
            onShowSnackbar("All memories deleted", null)
        },
        onShowMemoriesSheet = viewModel::onShowMemoriesSheet,
        onDeleteMemory = viewModel::onDeleteMemory,
        onOpenToS = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pocketcrew.ai/terms"))
            context.startActivity(intent)
        },
        onShowFeedbackSheet = viewModel::onShowFeedbackSheet,
        onFeedbackTextChange = viewModel::onFeedbackTextChange,
        onSubmitFeedback = {
            viewModel.onSubmitFeedback()
            onShowSnackbar("Feedback submitted. Thank you!", null)
        },
        onNavigateToModelDownload = onNavigateToModelDownload,
        onShowModelConfigSheet = viewModel::onShowModelConfigSheet,
        onSelectModelType = viewModel::onSelectModelType,
        onBackToModelList = viewModel::onBackToModelList,
        onHuggingFaceModelNameChange = viewModel::onHuggingFaceModelNameChange,
        onTemperatureChange = viewModel::onTemperatureChange,
        onTopKChange = viewModel::onTopKChange,
        onTopPChange = viewModel::onTopPChange,
        onSaveModelConfig = viewModel::onSaveModelConfig
    )
}
