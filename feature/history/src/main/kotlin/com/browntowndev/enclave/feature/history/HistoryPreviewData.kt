package com.browntowndev.enclave.feature.history

import com.browntowndev.enclave.domain.model.chat.ChatId

object HistoryPreviewData {
    val loadedUiState: HistoryUiState =
        HistoryUiState(
            pinnedChats =
                listOf(
                    HistoryChat(
                        id = ChatId("preview-pinned-1"),
                        name = "Agent planning session",
                        lastMessageDateTime = "Today, 2:30 PM",
                        isPinned = true,
                    ),
                    HistoryChat(
                        id = ChatId("preview-pinned-2"),
                        name = "Vault migration notes",
                        lastMessageDateTime = "Yesterday",
                        isPinned = true,
                    ),
                ),
            otherChats =
                listOf(
                    HistoryChat(
                        id = ChatId("preview-recent-1"),
                        name = "Weekend build ideas",
                        lastMessageDateTime = "Mon, 9:14 AM",
                        isPinned = false,
                    ),
                    HistoryChat(
                        id = ChatId("preview-recent-2"),
                        name = "On-device model benchmarks",
                        lastMessageDateTime = "Sun, 6:02 PM",
                        isPinned = false,
                    ),
                    HistoryChat(
                        id = ChatId("preview-recent-3"),
                        name = "Studio image batch",
                        lastMessageDateTime = "Sat, 11:48 AM",
                        isPinned = false,
                    ),
                ),
            isLoading = false,
        )

    val loadingUiState: HistoryUiState = HistoryUiState(isLoading = true)
}
