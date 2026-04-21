package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

data class ActiveChatTurnKey(
    val chatId: ChatId,
    val assistantMessageId: MessageId,
)

interface ActiveChatTurnSnapshotPort {
    fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?>

    suspend fun publish(
        key: ActiveChatTurnKey,
        snapshot: AccumulatedMessages,
    )

    suspend fun markSourcesExtracted(
        key: ActiveChatTurnKey,
        urls: List<String>,
    )

    suspend fun acknowledgeHandoff(key: ActiveChatTurnKey)

    suspend fun clear(key: ActiveChatTurnKey)
}
