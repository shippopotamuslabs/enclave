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

    suspend fun markWebSearchSourcesExtracted(
        key: ActiveChatTurnKey,
        urls: List<String>,
    )

    suspend fun attachArtifact(
        key: ActiveChatTurnKey,
        assistantMessageId: MessageId,
        artifact: com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest,
    )

    suspend fun attachMedia(
        key: ActiveChatTurnKey,
        assistantMessageId: MessageId,
        mediaUris: List<String>,
        mediaIds: List<String> = emptyList(),
        mediaTypes: List<String> = emptyList(),
        prompts: List<String> = emptyList(),
    )

    suspend fun acknowledgeHandoff(key: ActiveChatTurnKey)

    suspend fun clear(key: ActiveChatTurnKey)

    suspend fun getSnapshot(key: ActiveChatTurnKey): AccumulatedMessages?
}
