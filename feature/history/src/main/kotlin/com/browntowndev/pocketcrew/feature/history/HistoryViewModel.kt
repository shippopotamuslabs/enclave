package com.browntowndev.pocketcrew.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.usecase.chat.DeleteChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetAllChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.RenameChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.TogglePinChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.browntowndev.pocketcrew.domain.usecase.chat.SearchChatsUseCase

/**
 * ViewModel for the History screen.
 * Wires the UI to the repository via use cases.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getAllChatsUseCase: GetAllChatsUseCase,
    private val searchChatsUseCase: SearchChatsUseCase,
    private val deleteChatUseCase: DeleteChatUseCase,
    private val renameChatUseCase: RenameChatUseCase,
    private val togglePinChatUseCase: TogglePinChatUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val errorHandler: ViewModelErrorHandler,
) : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.update { query }
    }

    private data class CategorizedChats(
        val pinned: List<HistoryChat>,
        val other: List<HistoryChat>
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        _searchQuery.debounce(300L).flatMapLatest { query ->
            if (query.isBlank()) {
                getAllChatsUseCase().catch { emit(emptyList()) }
            } else {
                searchChatsUseCase(query).catch { emit(emptyList()) }
            }
        },
        getSettingsUseCase().catch { 
            emit(
                com.browntowndev.pocketcrew.domain.port.repository.SettingsData(
                    theme = com.browntowndev.pocketcrew.domain.model.settings.AppTheme.SYSTEM,
                    hapticPress = true,
                    hapticResponse = true,
                    customizationEnabled = false,
                    selectedPromptOption = com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption.CONCISE,
                    customPromptText = "",
                    allowMemories = false
                )
            ) 
        }
    ) { chats, settings ->
        val categorized = categorizeChats(chats)
        HistoryUiState(
            pinnedChats = categorized.pinned,
            otherChats = categorized.other,
            isLoading = false,
            hapticPress = settings.hapticPress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true),
    )

    private fun categorizeChats(chats: List<Chat>): CategorizedChats {
        val sorted = chats.sortedWith(
            compareByDescending<Chat> { it.pinned }
                .thenByDescending { it.lastModified }
        )
        val partitioned = sorted.partition { it.pinned }
        return CategorizedChats(
            pinned = partitioned.first.map { it.toHistoryChat() },
            other = partitioned.second.map { it.toHistoryChat() }
        )
    }

    fun deleteChat(id: Long) {
        viewModelScope.launch {
            try {
                deleteChatUseCase(id)
            } catch (e: Exception) {
                errorHandler.handleError(TAG, "Failed to delete chat with id: $id", e, "Failed to delete chat")
            }
        }
    }

    fun renameChat(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                renameChatUseCase(id, newName)
            } catch (e: Exception) {
                errorHandler.handleError(TAG, "Failed to rename chat with id: $id to: $newName", e, "Failed to rename chat")
            }
        }
    }

    fun pinChat(id: Long) = togglePin(id)

    fun unpinChat(id: Long) = togglePin(id)

    private fun togglePin(id: Long) {
        viewModelScope.launch {
            try {
                togglePinChatUseCase(id)
            } catch (e: Exception) {
                errorHandler.handleError(TAG, "Failed to update pin status for chat id: $id", e, "Failed to update pin status")
            }
        }
    }
}
