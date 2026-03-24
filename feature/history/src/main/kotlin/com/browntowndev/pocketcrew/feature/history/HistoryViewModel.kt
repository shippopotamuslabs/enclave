package com.browntowndev.pocketcrew.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Base state for history-specific mutable state (chats, loading, etc.).
 * Excludes settings which come from a separate flow.
 */
private data class HistoryBaseState(
    val pinnedChats: List<HistoryChat> = emptyList(),
    val otherChats: List<HistoryChat> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
) : ViewModel() {

    private val _baseState = MutableStateFlow(
        HistoryBaseState(
            pinnedChats = listOf(
                HistoryChat(1, "Project Alpha", "Today, 10:30 AM", true),
                HistoryChat(2, "Grocery List", "Yesterday, 6:15 PM", true)
            ),
            otherChats = listOf(
                HistoryChat(3, "Meeting Notes", "Oct 24, 2:00 PM", false),
                HistoryChat(4, "Weekend Plans", "Oct 22, 9:45 AM", false)
            ),
        ),
    )

    val uiState: StateFlow<HistoryUiState> = combine(
        _baseState,
        getSettingsUseCase(),
    ) { base, settings ->
        HistoryUiState(
            pinnedChats = base.pinnedChats,
            otherChats = base.otherChats,
            isLoading = base.isLoading,
            hapticPress = settings.hapticPress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun deleteChat(id: Long) {
        // Stub
    }

    fun renameChat(id: Long, newName: String) {
        // Stub
    }

    fun pinChat(id: Long) {
        // Stub
    }

    fun unpinChat(id: Long) {
        // Stub
    }
}
