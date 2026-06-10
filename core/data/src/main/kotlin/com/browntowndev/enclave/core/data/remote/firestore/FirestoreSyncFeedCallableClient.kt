package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class BackfillSyncFeedResult(
    val status: String,
    val eventsWritten: Int,
    val nextSequence: Long,
)

internal class FirestoreSyncFeedCallableClient(
    private val functions: FirebaseFunctions,
    private val loggingPort: LoggingPort,
) {
    suspend fun backfillIfNeeded(userId: String): BackfillSyncFeedResult? =
        runCatching {
            val result =
                functions
                    .getHttpsCallable(BACKFILL_CALLABLE)
                    .call()
                    .await()
            val payload = result.data as? Map<*, *> ?: return@runCatching null
            BackfillSyncFeedResult(
                status = payload["status"] as? String ?: "unknown",
                eventsWritten = (payload["eventsWritten"] as? Number)?.toInt() ?: 0,
                nextSequence = (payload["nextSequence"] as? Number)?.toLong() ?: 0L,
            )
        }.onFailure { failure ->
            loggingPort.error(
                FirestoreSyncContext.TAG,
                "backfillSyncFeed callable failed for uid=$userId",
                failure,
            )
        }.getOrNull()

    companion object {
        const val BACKFILL_CALLABLE = "backfillSyncFeedCallable"
    }
}
