package com.browntowndev.enclave.core.data.local.dao.message

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId

@Dao
interface MessageSyncCommandDao {
    @Query(
        "UPDATE message SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE chat_id = :chatId AND deleted = 0",
    )
    suspend fun softDeleteAllActiveByChatId(
        chatId: ChatId,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("UPDATE message SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<MessageId>)

    @Upsert
    suspend fun insertWebSearchSources(sources: List<WebSearchSourceEntity>)

    @Query("UPDATE message SET artifacts_json = :artifactsJson, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun updateMessageArtifacts(
        id: MessageId,
        artifactsJson: String?,
        timestamp: Long = System.currentTimeMillis(),
    )
}
