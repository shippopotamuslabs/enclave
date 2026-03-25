package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(query: String): Flow<List<Chat>> {
        return chatRepository.searchChats(query = query, ftsQuery = query)
    }
}
