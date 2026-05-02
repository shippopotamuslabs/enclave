package com.browntowndev.pocketcrew.feature.artifact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ArtifactRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtifactViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtifactStudioScreen(
        uiState = uiState,
        onBackClick = onNavigateBack,
        onExportPdf = viewModel::exportToPdf,
        modifier = modifier
    )
}
