package com.browntowndev.pocketcrew.presentation.screen.history

data class HistoryChat(
    val id: Long,
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
