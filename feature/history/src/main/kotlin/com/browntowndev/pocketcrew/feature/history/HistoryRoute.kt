package com.browntowndev.pocketcrew.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.pocketcrew.domain.model.chat.ChatId

@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (ChatId?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    HistoryScreen(
        uiState = uiState,
        searchQuery = searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onBackClick = onNavigateBack,
        onChatClick = onNavigateToChat,
        onNewChatClick = { onNavigateToChat(null) },
        onDeleteChat = viewModel::deleteChat,
        onRenameChat = viewModel::renameChat,
        onPinChat = viewModel::pinChat,
        onUnpinChat = viewModel::unpinChat,
        onSettingsClick = onNavigateToSettings,
        onShowSnackbar = onShowSnackbar
    )
}
