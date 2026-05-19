package com.browntowndev.pocketcrew.presentation.navigation

import com.browntowndev.pocketcrew.domain.model.chat.ChatId

data class ChatNavActions(
    val onNavigateToHistory: () -> Unit,
    val onNewChatFromChat: () -> Unit,
    val onNewChatFromShared: () -> Unit,
    val onNavigateToStudio: (
        ids: List<String>,
        uris: List<String>,
        types: List<String>,
        prompts: List<String>,
    ) -> Unit,
    val onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
)

data class HistoryNavActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToChat: (ChatId?) -> Unit,
    val onNavigateToStudio: () -> Unit,
    val onNavigateToGallery: () -> Unit,
    val onNavigateToSettings: () -> Unit,
)

data class StudioNavActions(
    val onNavigateToHistory: () -> Unit,
    val onNavigateToGallery: () -> Unit,
    val onNavigateBack: () -> Unit,
    val onNavigateToStudioDetail: (assetId: String) -> Unit,
    val onNavigateToGalleryDetail: (albumId: String, assetId: String) -> Unit,
    val onSendToStudioFromGallery: (assetId: String, mode: String, prompt: String?) -> Unit,
    val onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
)
