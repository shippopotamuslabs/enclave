package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.usecase.chat.MergeMessagesUseCase

internal fun projectChatMessages(
    dbMessages: List<Message>,
    activeSnapshot: AccumulatedMessages?,
    chatId: ChatId?,
    mergeMessagesUseCase: MergeMessagesUseCase,
): List<Message> {
    val dbMessagesMap: Map<MessageId, Message> = dbMessages.associateBy { message -> message.id }
    val activeMessagesMap: Map<MessageId, Message> = activeSnapshot
        ?.messages
        ?.mapValues { (_, snapshot) -> snapshot.toMessage(chatId) }
        .orEmpty()

    val mergedMessages = dbMessagesMap.mapValues { (id, dbMessage) ->
        val activeMessage = activeMessagesMap[id]
        mergeMessagesUseCase(dbMessage, activeMessage) ?: dbMessage
    }

    return (mergedMessages + activeMessagesMap.filterKeys { id -> id !in mergedMessages })
        .values
        .sortedBy { message -> message.createdAt ?: Long.MAX_VALUE }
}

private fun MessageSnapshot.toMessage(chatId: ChatId?): Message {
    return Message(
        id = messageId,
        chatId = chatId ?: ChatId(""),
        role = Role.ASSISTANT,
        content = Content(text = content, pipelineStep = pipelineStep),
        thinkingRaw = thinkingRaw.ifBlank { null },
        thinkingDurationSeconds = thinkingDurationSeconds,
        thinkingStartTime = thinkingStartTime.takeIf { startTime -> startTime != 0L },
        thinkingEndTime = thinkingEndTime.takeIf { endTime -> endTime != 0L },
        createdAt = null,
        messageState = messageState,
        modelType = modelType,
        tavilySources = tavilySources,
    )
}
