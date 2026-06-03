package com.browntowndev.enclave.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ProfileBuilderRoute(
    onBack: () -> Unit,
    viewModel: ProfileBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Auto-navigate back once profile is saved
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onBack()
    }

    ProfileBuilderScreen(
        uiState = uiState,
        onBack = onBack,
        onModelSelected = viewModel::onModelSelected,
        onFilesSelected = viewModel::onFilesSelected,
        onProceedFromFiles = viewModel::onProceedFromFiles,
        onStartAnalysis = viewModel::startAnalysis,
        onProfileEdited = viewModel::onProfileContentEdited,
        onSaveProfile = viewModel::saveProfile,
        onGoToStep = viewModel::goToStep,
    )
}
