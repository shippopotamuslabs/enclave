package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSelectedChatModeUseCase @Inject constructor(
    private val repository: UiSelectionRepository,
) {
    operator fun invoke(): Flow<Mode> = repository.selectedChatMode
}

class SetSelectedChatModeUseCase @Inject constructor(
    private val repository: UiSelectionRepository,
) {
    suspend operator fun invoke(mode: Mode) = repository.setChatMode(mode)
}
