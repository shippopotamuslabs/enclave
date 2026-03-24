package com.browntowndev.pocketcrew.feature.history

import com.browntowndev.pocketcrew.domain.usecase.chat.GetAllChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * STUB: Test version of HistoryViewModel for TDD Red phase.
 * Accepts required dependencies and throws NotImplementedError for untested methods.
 */
class HistoryViewModelStub(
    private val getAllChatsUseCase: GetAllChatsUseCase,
    private val settingsUseCases: SettingsUseCases
) {
    val uiState: StateFlow<HistoryUiState> = MutableStateFlow(
        HistoryUiState(
            pinnedChats = emptyList(),
            otherChats = emptyList(),
            isLoading = true
        )
    )

    fun deleteChat(id: Long) {
        throw NotImplementedError("deleteChat not yet implemented - TDD Red Phase")
    }

    fun renameChat(id: Long, newName: String) {
        throw NotImplementedError("renameChat not yet implemented - TDD Red Phase")
    }

    fun pinChat(id: Long) {
        throw NotImplementedError("pinChat not yet implemented - TDD Red Phase")
    }

    fun unpinChat(id: Long) {
        throw NotImplementedError("unpinChat not yet implemented - TDD Red Phase")
    }

    fun togglePinStatus(id: Long) {
        throw NotImplementedError("togglePinStatus not yet implemented - TDD Red Phase")
    }
}
