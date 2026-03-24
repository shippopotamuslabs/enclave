/*
 * Copyright 2024 Pocket Crew
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting all chats as a Flow.
 * Listens to database changes via Room Flow.
 * Returns chats sorted by pinned first, then by lastModified descending.
 */
class GetAllChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    /**
     * Returns all chats as a Flow, sorted by pinned first, then by lastModified descending.
     */
    operator fun invoke(): Flow<List<Chat>> {
        return chatRepository.getAllChats()
    }
}
