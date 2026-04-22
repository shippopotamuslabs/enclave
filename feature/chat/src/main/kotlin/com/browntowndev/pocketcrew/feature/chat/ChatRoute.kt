package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(
    onNavigateToHistory: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    onNewChat: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        onImageSelected = viewModel::onImageSelected,
        onClearImage = viewModel::clearSelectedImage,
        onShieldTap = {
            val reason = viewModel.getShieldReason()
            if (reason != null) {
                onShowSnackbar("Procedural harm blocked – $reason", null)
            }
        },
        onMicClick = viewModel::onMicClick,
    )
}
