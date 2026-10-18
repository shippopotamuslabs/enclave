package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult
import com.browntowndev.enclave.domain.model.security.VaultMetadata
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import kotlinx.coroutines.delay

/**
 * In-memory [CloudSyncPort] fake (ENC-28). Records every call in order in [calls];
 * results are test-controlled vars. Mirrors the defaults the MockK relaxed mocks
 * provided (successful no-ops).
 */
class FakeCloudSyncPort : CloudSyncPort {
    val calls = mutableListOf<String>()

    var syncAllResult: Result<Unit> = Result.success(Unit)
    var syncRemoteResult: Result<CloudSyncEventReplayResult> =
        Result.success(CloudSyncEventReplayResult(eventsApplied = 0, lastAppliedSequence = 0, hasMore = false))
    var vaultSaltResult: Result<String?> = Result.success(null)
    var wrappedDekResult: Result<String?> = Result.success(null)
    var verificationResult: Result<String?> = Result.success(null)

    /** Artificial latency for getVaultSalt (virtual-time friendly). */
    var vaultSaltDelayMs: Long = 0

    var getVerificationDataCallCount = 0

    /** Optional shared event log for cross-collaborator ordering assertions. */
    var events: MutableList<String>? = null

    private fun record(name: String) {
        calls += name
        events?.add(name)
    }

    fun count(name: String): Int = calls.count { it == name }

    var remainingEventsResult: Result<Long> = Result.success(0L)

    override suspend fun getRemainingEventsCount(): Result<Long> {
        record("getRemainingEventsCount")
        return remainingEventsResult
    }

    override fun startListening() {
        record("startListening")
    }

    override fun stopListening() {
        record("stopListening")
    }

    override suspend fun syncAllLocalDataToCloud(): Result<Unit> {
        record("syncAll")
        return syncAllResult
    }

    override suspend fun syncEntitiesToCloud(chats: List<Chat>, messages: List<Message>): Result<Unit> {
        record("syncEntities")
        return Result.success(Unit)
    }

    override suspend fun syncPendingChanges(): Result<Unit> {
        record("syncPending")
        return Result.success(Unit)
    }

    override suspend fun syncRemoteChangesFromCloud(): Result<CloudSyncEventReplayResult> {
        record("syncRemote")
        return syncRemoteResult
    }

    override suspend fun updateVaultSalt(salt: String): Result<Unit> {
        record("updateVaultSalt")
        return Result.success(Unit)
    }

    override suspend fun getVaultSalt(): Result<String?> {
        record("getVaultSalt")
        if (vaultSaltDelayMs > 0) delay(vaultSaltDelayMs)
        return vaultSaltResult
    }

    override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> {
        record("updateVaultVerificationData")
        return Result.success(Unit)
    }

    override suspend fun getVaultVerificationData(): Result<String?> {
        getVerificationDataCallCount++
        record("getVaultVerificationData")
        return verificationResult
    }

    override suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> {
        record("updateWrappedDek")
        return Result.success(Unit)
    }

    override suspend fun getWrappedDek(): Result<String?> {
        record("getWrappedDek")
        return wrappedDekResult
    }

    override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> {
        record("uploadVaultMetadata")
        return Result.success(Unit)
    }

    override suspend fun fetchVaultMetadata(): Result<VaultMetadata?> {
        record("fetchVaultMetadata")
        return Result.success(null)
    }

    override fun enqueueEmbeddingRegeneration() {
        record("enqueueEmbeddingRegeneration")
    }

    override suspend fun deleteUserAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllChatsFromCloud(): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllMediaFromCloud(): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllMemoriesFromCloud(): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllArtifactsFromCloud(): Result<Unit> = Result.success(Unit)

    override suspend fun bootstrapFeatureSettings(): Result<Unit> {
        record("bootstrapFeatureSettings")
        return Result.success(Unit)
    }

    override fun triggerSync() {
        record("triggerSync")
    }
}
