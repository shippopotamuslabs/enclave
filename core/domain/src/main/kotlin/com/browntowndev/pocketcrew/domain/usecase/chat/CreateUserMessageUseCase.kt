package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
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
private object CreateUserMessageConstants {
    val whitespaceRegex = Regex("\\s+")
}

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
                val chatName = generateChatName(message.content.text)
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
                    content = Content(text = ""),
                    role = Role.ASSISTANT
                )
                val assistantMessageId = messageRepository.saveMessage(assistantMessage)
                PromptResult(userMessageId, assistantMessageId, newChatId)
            } else {
                val userMessageId = messageRepository.saveMessage(message)

                // Create placeholder assistant message for existing chat too
                val assistantMessage = Message(
                    chatId = message.chatId,
                    content = Content(text = ""),
                    role = Role.ASSISTANT
                )
                val assistantMessageId = messageRepository.saveMessage(assistantMessage)
                PromptResult(userMessageId, assistantMessageId, message.chatId)
            }
        }
    }

    /**
     * Generates a chat name from the message content.
     * 
     * | Input | Output |
     * |-------|--------|
     * | Empty content | "New Chat" (default) |
     * | Single word | That word only |
     * | 2-5 words | First N words (N = actual count) |
     * | More than 5 words | First 5 words |
     * | No words (split fails) AND length > 30 | First 30 chars + "..." |
     * | Special characters | Included as-is |
     * | Numbers only | Allowed as chat name |
     *
     * @param content The message content
     * @return The generated chat name
     */
    private fun generateChatName(content: String): String {
        // Empty content returns default name
        if (content.isBlank()) {
            return "New Chat"
        }
        
        val words = content.split(CreateUserMessageConstants.whitespaceRegex)
        
        // If we have words, take up to 5
        if (words.isNotEmpty()) {
            val firstFiveWords = words.take(5)
            val result = firstFiveWords.joinToString(" ")
            
            // If we have more than 5 words, truncate to first 5
            if (words.size > 5) {
                return result
            }
            
            // If we got some words but content has no spaces and is > 30 chars
            // (meaning words.size == 1 and content.length > 30), truncate
            if (words.size == 1 && content.length > 30) {
                return content.take(30) + "..."
            }
            
            return result
        }
        
        // Fallback: no words but content exists - truncate if too long
        if (content.length > 30) {
            return content.take(30) + "..."
        }
        
        return content
    }
}
