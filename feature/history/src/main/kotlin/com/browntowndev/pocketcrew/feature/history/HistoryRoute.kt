package com.browntowndev.pocketcrew.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreen(
        uiState = uiState,
        onBackClick = onNavigateBack,
        onChatClick = onNavigateToChat,
        onNewChatClick = { onNavigateToChat(-1L) },
        onDeleteChat = viewModel::deleteChat,
        onRenameChat = viewModel::renameChat,
        onPinChat = viewModel::pinChat,
        onUnpinChat = viewModel::unpinChat,
        onSettingsClick = onNavigateToSettings,
        onShowSnackbar = onShowSnackbar
    )
}
