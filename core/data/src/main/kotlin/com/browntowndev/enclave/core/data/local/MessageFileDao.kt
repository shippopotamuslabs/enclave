@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.enclave.domain.model.chat.MessageId

@Dao
interface MessageFileDao {
    @Upsert
    suspend fun insert(entity: MessageFileEntity)

    @Query("SELECT * FROM message_file WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): MessageFileEntity?

    @Query("SELECT * FROM message_file WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): MessageFileEntity?

    @Query("SELECT * FROM message_file WHERE message_id = :messageId AND deleted = 0")
    suspend fun getByMessageId(messageId: MessageId): List<MessageFileEntity>

    @Query("UPDATE message_file SET deleted = 1, sync_status = 'PENDING_UPLOAD', updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("SELECT * FROM message_file WHERE sync_status = 'PENDING_UPLOAD' AND deleted = 0")
    suspend fun getPendingUploads(): List<MessageFileEntity>

    @Query("SELECT * FROM message_file WHERE deleted = 1 AND sync_status != 'DELETED'")
    suspend fun getPendingDeletions(): List<MessageFileEntity>

    @Query("UPDATE message_file SET sync_status = 'DELETED', updated_at = :timestamp WHERE id = :id")
    suspend fun markCloudDeleted(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message_file SET sync_status = :status, updated_at = :timestamp WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message_file SET staging_remote_url = :url WHERE id = :id")
    suspend fun updateStagingUrl(
        id: String,
        url: String?,
    )

    @Query(
        "UPDATE message_file SET sync_status = 'UPLOADED', remote_url = :remoteUrl, staging_remote_url = NULL, pending_sync = 1, updated_at = :timestamp WHERE id = :id",
    )
    suspend fun markUploaded(
        id: String,
        remoteUrl: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM message_file WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("UPDATE message_file SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM message_file WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<MessageFileEntity>

    @Query("UPDATE message_file SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
