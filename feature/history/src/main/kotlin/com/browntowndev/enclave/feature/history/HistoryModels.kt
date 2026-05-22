package com.browntowndev.enclave.feature.history

import com.browntowndev.enclave.domain.model.chat.ChatId

data class HistoryChat(
    val id: ChatId,
    val name: String,
    val lastMessageDateTime: String,
    val isPinned: Boolean,
    val isSyncing: Boolean = false,
)

data class HistoryUiState(
    val pinnedChats: List<HistoryChat> = emptyList(),
    val otherChats: List<HistoryChat> = emptyList(),
    val isLoading: Boolean = false,
)
