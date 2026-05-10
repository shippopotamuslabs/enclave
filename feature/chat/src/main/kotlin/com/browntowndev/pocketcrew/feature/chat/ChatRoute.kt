package com.browntowndev.pocketcrew.feature.chat

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(
    onNavigateToHistory: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    onNewChat: () -> Unit,
    onNavigateToArtifactStudio: (com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest) -> Unit,
    onNavigateToStudio: (ids: List<String>, uris: List<String>, types: List<String>, prompts: List<String>) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.shareUiEvents.collect { event ->
            when (event) {
                is ShareUiEvent.OpenShareSheet -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.shareUrl)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Chat"))
                }
            }
        }
    }

    ChatScreen(
        uiState = uiState,
        onNavigateToHistory = onNavigateToHistory,
        onNewChat = {
            viewModel.createNewChat()
            onNewChat()
        },
        onSendMessage = { viewModel.onSendMessage() },
        onStopGenerating = viewModel::stopGeneration,
        onModeChange = viewModel::onModeChange,
        onInputChange = viewModel::onInputChange,
        onEditMessage = viewModel::onEditMessage,
        onPlayAudio = viewModel::onPlayTts,
        onImagesSelected = viewModel::onImagesSelected,
        onRemoveImage = viewModel::removeImage,
        onFileSelected = viewModel::onFileSelected,
        onClearFile = viewModel::clearSelectedFile,
        onShieldTap = {
            val reason = viewModel.getShieldReason()
            if (reason != null) {
                onShowSnackbar("Procedural harm blocked – $reason", null)
            }
        },
        onMicClick = viewModel::onMicClick,
        onNavigateToArtifact = onNavigateToArtifactStudio,
        onNavigateToStudio = onNavigateToStudio,
        onShareClick = viewModel::onShareClick,
        onShareTurn = viewModel::onShareTurn,
        onShareFullChat = viewModel::onShareFullChat,
        onDismissShareSheet = viewModel::onDismissShareSheet,
    )
}
