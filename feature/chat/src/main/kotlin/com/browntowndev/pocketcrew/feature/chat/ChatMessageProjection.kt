package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import java.util.LinkedHashMap

internal data class ChatMessageProjectionResult(
    val messages: List<Message>,
    val handoffReady: Boolean,
)

/**
 * Stateful projection cache.
 *
 * Full-result memo: when all three inputs are reference-equal to the prior call, returns
 * the same [ChatMessageProjectionResult] reference with no allocations.
 *
 * Per-message cache: keyed on (dbMessage, snapshot) structural equality. Messages whose
 * inputs haven't changed reuse the previously projected [Message] instance, so downstream
 * structural-equality checks (e.g., Compose diffing) see stable references.
 *
 * Not thread-safe — must be created and used on a single coroutine (the combine lambda).
 */
internal class ChatMessageProjector {
    private data class MessageInputKey(
        val dbMessage: Message,
        val snapshot: MessageSnapshot?,
    )

    private val messageCache = HashMap<MessageId, Pair<MessageInputKey, Message>>()

    private var memoDbMessages: List<Message>? = null
    private var memoSnapshot: AccumulatedMessages? = null
    private var memoKey: ActiveChatTurnKey? = null
    private var memoResult: ChatMessageProjectionResult? = null

    fun project(
        dbMessages: List<Message>,
        activeSnapshot: AccumulatedMessages?,
        activeKey: ActiveChatTurnKey?,
    ): ChatMessageProjectionResult {
        val memo = memoResult
        if (memo != null && matchesMemo(dbMessages, activeSnapshot, activeKey)) {
            val handoffReady = computeHandoffReady(dbMessages, activeSnapshot, activeKey)
            return if (memo.handoffReady == handoffReady) memo else memo.copy(handoffReady = handoffReady)
        }

        val result = buildResult(dbMessages, activeSnapshot, activeKey)
        memoDbMessages = dbMessages
        memoSnapshot = activeSnapshot
        memoKey = activeKey
        memoResult = result
        return result
    }

    private fun matchesMemo(
        dbMessages: List<Message>,
        activeSnapshot: AccumulatedMessages?,
        activeKey: ActiveChatTurnKey?,
    ): Boolean =
        dbMessages === memoDbMessages &&
            activeSnapshot === memoSnapshot &&
            activeKey == memoKey

    private fun buildResult(
        dbMessages: List<Message>,
        activeSnapshot: AccumulatedMessages?,
        activeKey: ActiveChatTurnKey?,
    ): ChatMessageProjectionResult {
        val activeMessagesMap: Map<MessageId, MessageSnapshot> = activeSnapshot?.messages.orEmpty()
        val chatId: ChatId? = activeKey?.chatId ?: dbMessages.firstOrNull()?.chatId
        val dbMessagesMap: Map<MessageId, Message> = dbMessages.associateBy { it.id }

        val projectedMap = LinkedHashMap<MessageId, Message>(dbMessages.size + activeMessagesMap.size)

        dbMessages.forEach { dbMessage ->
            val snapshot = activeMessagesMap[dbMessage.id]
            val inputKey = MessageInputKey(dbMessage = dbMessage, snapshot = snapshot)
            val cached = messageCache[dbMessage.id]
            projectedMap[dbMessage.id] =
                if (cached != null && cached.first == inputKey) {
                    cached.second
                } else {
                    val projected = resolveMessage(chatId, dbMessage, snapshot)
                    messageCache[dbMessage.id] = inputKey to projected
                    projected
                }
        }

        activeMessagesMap.forEach { (messageId, snapshot) ->
            if (messageId !in projectedMap) {
                projectedMap[messageId] = snapshot.toOrphanLiveMessage(chatId = chatId)
            }
        }

        val sorted = projectedMap.values.sortedBy { it.createdAt ?: Long.MAX_VALUE }

        return ChatMessageProjectionResult(
            messages = sorted,
            handoffReady = computeHandoffReady(dbMessages, activeSnapshot, activeKey),
        )
    }

    private fun computeHandoffReady(
        dbMessages: List<Message>,
        activeSnapshot: AccumulatedMessages?,
        activeKey: ActiveChatTurnKey?,
    ): Boolean {
        val activeMessagesMap = activeSnapshot?.messages.orEmpty()
        val dbMessagesMap = dbMessages.associateBy { it.id }
        return activeSnapshot != null &&
            activeKey != null &&
            activeMessagesMap[activeKey.assistantMessageId]?.messageState == MessageState.COMPLETE &&
            dbMessagesMap[activeKey.assistantMessageId]?.messageState == MessageState.COMPLETE
    }

    private fun resolveMessage(
        chatId: ChatId?,
        dbMessage: Message,
        snapshot: MessageSnapshot?,
    ): Message =
        when {
            snapshot != null && snapshot.messageState != MessageState.COMPLETE ->
                snapshot.toMessage(chatId = chatId, createdAt = dbMessage.createdAt)
            snapshot != null && dbMessage.messageState != MessageState.COMPLETE ->
                snapshot.toMessage(chatId = chatId, createdAt = dbMessage.createdAt)
            else -> dbMessage
        }
}

internal fun projectChatMessages(
    dbMessages: List<Message>,
    activeSnapshot: AccumulatedMessages?,
    activeKey: ActiveChatTurnKey?,
): ChatMessageProjectionResult = ChatMessageProjector().project(dbMessages, activeSnapshot, activeKey)

/**
 * Snapshot-only rows (Room has not caught up yet) should not jump straight to THINKING/GENERATING.
 * Show PROCESSING until the durable db row exists and [resolveMessage] can take over.
 */
private fun MessageSnapshot.toOrphanLiveMessage(chatId: ChatId?): Message {
    val message = toMessage(chatId = chatId)
    return when (message.messageState) {
        MessageState.THINKING, MessageState.GENERATING ->
            message.copy(
                messageState = MessageState.PROCESSING,
                thinkingRaw = null,
                thinkingDurationSeconds = null,
                thinkingStartTime = null,
                thinkingEndTime = null,
                transcriptSegments = emptyList(),
            )
        else -> message
    }
}

private fun MessageSnapshot.toMessage(
    chatId: ChatId?,
    createdAt: Long? = null,
): Message =
    Message(
        id = messageId,
        chatId = chatId,
        role = Role.ASSISTANT,
        content =
            Content(
                text = content,
                pipelineStep = pipelineStep,
                artifacts = if (messageState == MessageState.COMPLETE) artifacts else emptyList(),
            ),
        thinkingRaw = thinkingRaw.ifBlank { null },
        thinkingDurationSeconds = thinkingDurationSeconds,
        thinkingStartTime = thinkingStartTime.takeIf { startTime -> startTime != 0L },
        thinkingEndTime = thinkingEndTime.takeIf { endTime -> endTime != 0L },
        createdAt = createdAt,
        messageState = messageState,
        modelType = modelType,
        webSearchSources = webSearchSources,
        transcriptSegments = transcriptSegments,
    )
