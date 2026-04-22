package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import java.util.LinkedHashMap

internal data class ChatMessageProjectionResult(
    val messages: List<Message>,
    val handoffReady: Boolean,
)

internal fun projectChatMessages(
    dbMessages: List<Message>,
    activeSnapshot: AccumulatedMessages?,
    activeKey: ActiveChatTurnKey?,
): ChatMessageProjectionResult {
    val dbMessagesMap: Map<MessageId, Message> = dbMessages.associateBy { message -> message.id }
    val activeMessagesMap: Map<MessageId, MessageSnapshot> = activeSnapshot
        ?.messages
        .orEmpty()

    val chatId = activeKey?.chatId ?: dbMessages.firstOrNull()?.chatId ?: ChatId("")
    val projectedMessages = LinkedHashMap<MessageId, Message>()
    dbMessagesMap.forEach { (id, dbMessage) ->
        val activeMessage = activeMessagesMap[id]
        projectedMessages[id] = when {
            activeMessage == null -> dbMessage
            dbMessage.shouldPreferSnapshot(activeMessage) -> activeMessage.toMessage(
                chatId = chatId,
                createdAt = dbMessage.createdAt,
            )
            else -> dbMessage
        }
    }

    activeMessagesMap.forEach { (messageId, snapshot) ->
        if (messageId !in projectedMessages) {
            projectedMessages[messageId] = snapshot.toMessage(chatId = chatId)
        }
    }

    val projected = projectedMessages.values
        .sortedBy { message -> message.createdAt ?: Long.MAX_VALUE }

    return ChatMessageProjectionResult(
        messages = projected,
        handoffReady = activeSnapshot != null &&
            activeKey != null &&
            activeMessagesMap.isNotEmpty() &&
            activeMessagesMap.all { (messageId, snapshot) ->
                dbMessagesMap[messageId]?.canRetireSnapshot(snapshot) == true
            },
    )
}

private fun MessageSnapshot.toMessage(
    chatId: ChatId,
    createdAt: Long? = null,
): Message {
    return Message(
        id = messageId,
        chatId = chatId,
        role = Role.ASSISTANT,
        content = Content(text = content, pipelineStep = pipelineStep),
        thinkingRaw = thinkingRaw.ifBlank { null },
        thinkingDurationSeconds = thinkingDurationSeconds,
        thinkingStartTime = thinkingStartTime.takeIf { startTime -> startTime != 0L },
        thinkingEndTime = thinkingEndTime.takeIf { endTime -> endTime != 0L },
        createdAt = createdAt,
        messageState = messageState,
        modelType = modelType,
        tavilySources = tavilySources,
    )
}

private fun Message.shouldPreferSnapshot(snapshot: MessageSnapshot): Boolean {
    return !canRetireSnapshot(snapshot)
}

private fun Message.canRetireSnapshot(snapshot: MessageSnapshot): Boolean {
    if (messageState != MessageState.COMPLETE) return false
    if (!snapshot.isComplete && snapshot.messageState != MessageState.COMPLETE) return false

    val dbText = content.text
    val snapshotText = snapshot.content
    if (dbText.length < snapshotText.length) return false
    if (!dbText.startsWith(snapshotText)) return false

    return true
}
