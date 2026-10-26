package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.sync.isPermissionDenied
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class BackfillSyncFeedResult(
    val status: String,
    val eventsWritten: Int,
    val nextSequence: Long,
)

internal open class FirestoreSyncFeedCallableClient(
    private val functions: FirebaseFunctions,
    private val loggingPort: LoggingPort,
) {
    @Volatile
    private var permissionDeniedStopped = false

    open suspend fun backfillIfNeeded(userId: String): BackfillSyncFeedResult? {
        if (permissionDeniedStopped) return null
        return invokeBackfillCallable(userId)
    }

    /**
     * Marks an account whose feed already has trigger-written events as initialized without
     * re-scanning every entity collection (migration path for pre-backfill accounts).
     */
    open suspend fun markFeedInitializedIfNeeded(userId: String): BackfillSyncFeedResult? {
        if (permissionDeniedStopped) return null
        return invokeBackfillCallable(userId, mapOf("migrationOnly" to true))
    }

    private suspend fun invokeBackfillCallable(
        userId: String,
        data: Map<String, Any> = emptyMap(),
    ): BackfillSyncFeedResult? =
        runCatching {
            val result =
                functions
                    .getHttpsCallable(BACKFILL_CALLABLE)
                    .call(data)
                    .await()
            val payload = result.data as? Map<*, *> ?: return@runCatching null
            BackfillSyncFeedResult(
                status = payload["status"] as? String ?: "unknown",
                eventsWritten = (payload["eventsWritten"] as? Number)?.toInt() ?: 0,
                nextSequence = (payload["nextSequence"] as? Number)?.toLong() ?: 0L,
            )
        }.onFailure { failure ->
            if (failure.isPermissionDenied()) {
                if (!permissionDeniedStopped) {
                    permissionDeniedStopped = true
                    loggingPort.warning(
                        FirestoreSyncContext.TAG,
                        "backfillSyncFeed callable denied (subscription required); stopping further calls",
                    )
                }
            } else {
                loggingPort.error(
                    FirestoreSyncContext.TAG,
                    "backfillSyncFeed callable failed for uid=$userId",
                    failure,
                )
            }
        }.getOrNull()

    companion object {
        const val BACKFILL_CALLABLE = "backfillSyncFeedCallable"
    }
}
