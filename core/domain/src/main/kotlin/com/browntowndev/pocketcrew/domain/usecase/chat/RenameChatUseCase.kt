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

import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import javax.inject.Inject

/**
 * Use case for renaming a chat.
 */
class RenameChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    /**
     * Renames a chat.
     *
     * @param chatId The ID of the chat to rename
     * @param newName The new name for the chat
     */
    suspend operator fun invoke(chatId: Long, newName: String) {
        chatRepository.renameChat(chatId, newName)
    }
}
