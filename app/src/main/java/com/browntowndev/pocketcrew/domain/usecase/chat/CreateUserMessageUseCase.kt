package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import java.util.Date
import javax.inject.Inject

/**
 * Use case for processing a user prompt by creating a message.
 *
 * This use case demonstrates the transactional pattern: it wraps the repository
 * calls inside a transaction to ensure atomicity. If the message has no
 * associated chat (id == -1), it creates a new chat first and updates the
 * message's chatId before saving.
 *
 * Why wrap a single call in a transaction:
 * - Provides a consistent API for business operations
 * - Makes it easy to add more operations later without changing the pattern
 * - Transaction boundaries are a business logic concern, not a data layer concern
 *
 * @param transactionProvider Provides transaction semantics
 * @param messageRepository Handles message persistence
 * @param chatRepository Handles chat persistence
 */
class CreateUserMessageUseCase @Inject constructor(
    private val transactionProvider: TransactionProvider,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository
) {
    /**
     * Result of processing a user prompt - contains IDs needed for the response flow.
     */
    data class PromptResult(
        val userMessageId: Long,
        val assistantMessageId: Long,
        val chatId: Long
    )

    /**
     * Processes a prompt by saving the user message to the database.
     * If the message has no associated chat (id == -1), creates a new chat first.
     * Also creates a placeholder assistant message that will be populated after generation.
     * The save operation is wrapped in a transaction to ensure atomicity.
     *
     * @param message The message to save
     * @return PromptResult containing the assistant message ID and chat ID
     */
    suspend operator fun invoke(message: Message): PromptResult {
        return transactionProvider.runInTransaction {
            // If message.chatId == 0, create a new chat and update message.chatId
            // (0 is the default value that triggers auto-generation in Room)
            if (message.chatId == 0L) {
                val now = Date()
                val chatName = generateChatName(message.content)
                val newChat = Chat(
                    name = chatName,
                    created = now,
                    lastModified = now,
                    pinned = false
                )
                val newChatId = chatRepository.createChat(newChat)
                val updatedMessage = message.copy(chatId = newChatId)
                val userMessageId = messageRepository.saveMessage(updatedMessage)

                // Create placeholder assistant message (empty content, will be updated after generation)
                val assistantMessage = Message(
                    chatId = newChatId,
                    content = "",
                    role = Role.ASSISTANT
                )
                val assistantMessageId = messageRepository.saveMessage(assistantMessage)
                PromptResult(userMessageId, assistantMessageId, newChatId)
            } else {
                val userMessageId = messageRepository.saveMessage(message)

                // Create placeholder assistant message for existing chat too
                val assistantMessage = Message(
                    chatId = message.chatId,
                    content = "",
                    role = Role.ASSISTANT
                )
                val assistantMessageId = messageRepository.saveMessage(assistantMessage)
                PromptResult(userMessageId, assistantMessageId, message.chatId)
            }
        }
    }

    /**
     * Generates a chat name from the message content.
     * Takes the first 5 words of the content, or all words if fewer than 5.
     *
     * @param content The message content
     * @return The generated chat name
     */
    private fun generateChatName(content: String): String {
        val words = content.split("\\s+".toRegex())
        val firstFiveWords = words.take(5)
        return firstFiveWords.joinToString(" ")
    }
}
