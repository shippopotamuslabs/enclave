package com.browntowndev.pocketcrew.presentation.screen.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(
    onNavigateToHistory: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        uiState = uiState,
        onNavigateToHistory = onNavigateToHistory,
        onNewChat = { /* viewModel.createNewChat() stub */ },
        onSendMessage = { viewModel.onSendMessage() },
        onModeChange = viewModel::onModeChange,
        onInputChange = viewModel::onInputChange,
        onExpandToggle = viewModel::onExpandToggle,
        onAttach = viewModel::onAttach,
        onShieldTap = {
            onShowSnackbar("Procedural harm blocked – ${uiState.shieldReason}", null)
        },
    )
}
