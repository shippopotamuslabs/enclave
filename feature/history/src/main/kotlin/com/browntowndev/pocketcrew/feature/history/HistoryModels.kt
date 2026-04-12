package com.browntowndev.pocketcrew.feature.history

import com.browntowndev.pocketcrew.domain.model.chat.ChatId

data class HistoryChat(
    val id: ChatId,
    val name: String,
    val lastMessageDateTime: String,
    val isPinned: Boolean
)

data class HistoryUiState(
    val pinnedChats: List<HistoryChat> = emptyList(),
    val otherChats: List<HistoryChat> = emptyList(),
    val isLoading: Boolean = false,
    val hapticPress: Boolean = true,
)
