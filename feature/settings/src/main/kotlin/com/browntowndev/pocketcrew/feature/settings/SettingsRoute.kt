package com.browntowndev.pocketcrew.feature.settings
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType


@Composable
fun SettingsRoute(
    onCloseClick: () -> Unit,
    onNavigateToModelDownload: () -> Unit,
    onNavigateToModelConfigure: (ModelType) -> Unit,
    onNavigateToByokConfigure: () -> Unit,
    onNavigateToLocalModelConfigure: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreen(
        uiState = uiState,
        onCloseClick = onCloseClick,
        onThemeChange = viewModel::onThemeChange,
        onHapticPressChange = viewModel::onHapticPressChange,
        onHapticResponseChange = viewModel::onHapticResponseChange,
        onAlwaysUseVisionModelChange = viewModel::onAlwaysUseVisionModelChange,
        onBackgroundInferenceChange = viewModel::onBackgroundInferenceChange,
        onShowCustomizationSheet = viewModel::onShowCustomizationSheet,
        onShowDataControlsSheet = viewModel::onShowDataControlsSheet,
        onShowMemoriesSheet = viewModel::onShowMemoriesSheet,
        onOpenToS = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pocketcrew.ai/terms"))
            context.startActivity(intent)
        },
        onShowFeedbackSheet = viewModel::onShowFeedbackSheet,
        onShowVisionSettingsSheet = viewModel::onShowVisionSettingsSheet,
        onNavigateToModelConfigure = onNavigateToModelConfigure,
        onSetDefaultModel = viewModel::onSetDefaultModel,
        onShowLocalModelsSheet = viewModel::onShowModelConfigSheet, // Reuse this state flag
        onShowByokSheet = viewModel::onShowByokSheet,
        onNavigateToByokConfigure = onNavigateToByokConfigure,
        onStartCreateApiModelAsset = viewModel::onStartCreateApiModelAsset,
        onStartConfigureSearchSkill = viewModel::onStartConfigureSearchSkill,
        onSelectApiModelAsset = viewModel::onSelectApiModelAsset,
        onSelectApiModelConfig = viewModel::onSelectApiModelConfig,
        onDeleteApiModelAsset = viewModel::onDeleteApiModelAsset,
        onDeleteApiModelConfig = { id -> viewModel.onDeleteApiModelConfig(id, {}) },
        onNavigateToLocalModelConfigure = onNavigateToLocalModelConfigure,
        onSelectLocalModelAsset = viewModel::onSelectLocalModelAsset,
        onSelectLocalModelConfig = viewModel::onSelectLocalModelConfig,
        onDeleteLocalModelAsset = viewModel::onDeleteLocalModelAsset,
        onDeleteLocalModelConfig = { id -> viewModel.onDeleteLocalModelConfig(id, {}) },
        onConfirmDeletionWithReassignment = viewModel::onConfirmDeletionWithReassignment,
        onDismissDeletionSafety = viewModel::onDismissDeletionSafety
    )
}
