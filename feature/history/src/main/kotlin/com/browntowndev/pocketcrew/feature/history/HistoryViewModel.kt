package com.browntowndev.pocketcrew.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.usecase.chat.DeleteChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetAllChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.RenameChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.TogglePinChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the History screen.
 * Wires the UI to the repository via use cases.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getAllChatsUseCase: GetAllChatsUseCase,
    private val deleteChatUseCase: DeleteChatUseCase,
    private val renameChatUseCase: RenameChatUseCase,
    private val togglePinChatUseCase: TogglePinChatUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
) : ViewModel() {

    private data class CategorizedChats(
        val pinned: List<HistoryChat>,
        val other: List<HistoryChat>
    )

    val uiState: StateFlow<HistoryUiState> = combine(
        getAllChatsUseCase().catch { emit(emptyList()) },
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
        return CategorizedChats(
            pinned = sorted.filter { it.pinned }.map { it.toHistoryChat() },
            other = sorted.filter { !it.pinned }.map { it.toHistoryChat() }
        )
    }

    fun deleteChat(id: Long) {
        viewModelScope.launch {
            try {
                deleteChatUseCase(id)
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun renameChat(id: Long, newName: String) {
        viewModelScope.launch {
            try {
                renameChatUseCase(id, newName)
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun pinChat(id: Long) {
        viewModelScope.launch {
            try {
                togglePinChatUseCase(id)
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun unpinChat(id: Long) {
        viewModelScope.launch {
            try {
                togglePinChatUseCase(id)
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }
}
