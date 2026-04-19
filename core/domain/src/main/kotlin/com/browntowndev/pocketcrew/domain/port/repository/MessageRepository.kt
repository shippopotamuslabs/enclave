package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatSummary
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Port (interface) for message persistence.
 * Implemented by the data layer.
 *
 * Note: This repository handles saving messages to the message table
 * and maintaining the FTS search index. Transaction management is
 * delegated to the use case layer to keep this interface agnostic
 * of transaction lifecycle.
 */
interface MessageRepository {
    /**
     * Saves a message to the database.
     * This also handles updating the FTS search index.
     *
     * @param message The message to save
     * @return The ID of the saved message
     */
    suspend fun saveMessage(message: Message): MessageId

    /**
     * Retrieves a message by its ID.
     *
     * @param id The message ID
     * @return The message if found, null otherwise
     */
    suspend fun getMessageById(id: MessageId): Message?

    /**
     * Retrieves all messages for a specific chat, ordered by ID ascending.
     *
     * @param chatId The chat ID
     * @return List of messages in chronological order
     */
    suspend fun getMessagesForChat(chatId: ChatId): List<Message>

    suspend fun saveVisionAnalysis(
        userMessageId: MessageId,
        imageUri: String,
        promptText: String,
        analysisText: String,
        modelType: ModelType,
    )

    suspend fun getVisionAnalysesForMessages(
        userMessageIds: List<MessageId>
    ): Map<MessageId, List<MessageVisionAnalysis>>

    /**
     * Resolves the latest image-bearing user message for a chat.
     *
     * Resolution rules:
     * 1. If [currentUserMessageId] has an imageUri, return that message.
     * 2. Otherwise, find the most recent prior user message in the same chat
     *    with a non-null imageUri, ordered by createdAt DESC.
     * 3. Returns null if no image-bearing user message exists.
     *
     * @param chatId The chat to search within.
     * @param currentUserMessageId The current user message ID, preferred if it has an image.
     * @return A [ResolvedImageTarget] or null if no image-bearing message exists.
     */
    suspend fun resolveLatestImageBearingUserMessage(
        chatId: ChatId,
        currentUserMessageId: MessageId,
    ): ResolvedImageTarget?

    /**
     * Searches messages within a specific chat using full-text search.
     * Used by the search_chat tool to provide "infinite memory"
     * for details lost to context window summarization or FIFO eviction.
     *
     * @param chatId The chat ID to search within.
     * @param query The FTS-sanitized search query.
     * @return List of matching messages in chronological order.
     */
    suspend fun searchMessagesInChat(chatId: ChatId, query: String): List<Message>

    /**
     * Searches messages across all chats using full-text search.
     * Used by the search_chat_history tool to find relevant messages
     * from the user's past conversations.
     *
     * Multiple queries are OR'd together so any match is returned.
     *
     * @param queries The search queries to match against.
     * @return List of matching messages in chronological order, each with its chatId.
     */
    suspend fun searchMessagesAcrossChats(queries: List<String>): List<Message>

    /**
     * Retrieves messages surrounding a specific message in its chat.
     * Used for context expansion — when a matched message is found,
     * this returns N messages before and after it for better understanding.
     *
     * @param chatId The chat ID the message belongs to.
     * @param timestamp The created_at timestamp of the anchor message.
     * @param before Number of messages before the anchor to return.
     * @param after Number of messages after the anchor to return.
     * @return List of messages in chronological order around the anchor.
     */
    suspend fun getMessagesAround(chatId: ChatId, timestamp: Long, before: Int, after: Int): List<Message>

    /**
     * Retrieves the rolling summary of a chat.
     * The summary provides the LLM with high-level conversation context
     * for details lost to context window summarization or FIFO eviction.
     */
    suspend fun getChatSummary(chatId: ChatId): ChatSummary?

    /**
     * Saves or updates the summary for a chat.
     *
     * @param summary The summary to save
     */
    suspend fun saveChatSummary(summary: ChatSummary)

    /**
     * Deletes the summary for a chat.
     *
     * @param chatId The chat ID
     */
    suspend fun deleteChatSummary(chatId: ChatId)
}
