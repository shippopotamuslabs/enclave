package com.browntowndev.pocketcrew.core.data.remote.firestore

/**
 * Pages the remote `users/{uid}/sync_events` feed and attaches/detaches a single live
 * listener on the same feed. Exposes no Firestore types — the production gateway is
 * the only place Firestore-specific calls live so the rest of the data layer (and all
 * tests) stay framework-agnostic.
 */
internal class FirestoreSyncEventFeedClient(
    private val gateway: FirestoreSyncEventQueryGateway = ThrowingFirestoreSyncEventQueryGateway,
    private val pageSize: Long = DEFAULT_PAGE_SIZE,
) {
    data class QueryShape(
        val collectionPath: String,
        val filter: String,
        val orderBy: String,
        val limit: Long,
    )

    suspend fun fetchNextPage(
        userId: String,
        lastAppliedSequence: Long,
    ): List<FirestoreSyncEvent> {
        val shape = replayQueryShape(userId, lastAppliedSequence)
        return gateway.fetchEvents(shape)
    }

    fun replayQueryShape(
        userId: String,
        lastAppliedSequence: Long,
    ): QueryShape = buildQueryShape(userId, lastAppliedSequence)

    fun liveListenerQueryShape(
        userId: String,
        lastAppliedSequence: Long,
    ): QueryShape =
        buildQueryShape(
            userId = userId,
            lastAppliedSequence = lastAppliedSequence,
            limit = UNBOUNDED_LIVE_LISTENER,
        )

    suspend fun attachLiveListener(
        userId: String,
        lastAppliedSequence: Long,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration {
        val shape = liveListenerQueryShape(userId, lastAppliedSequence)
        return gateway.listen(shape, onEvents)
    }

    private fun buildQueryShape(
        userId: String,
        lastAppliedSequence: Long,
        limit: Long = pageSize,
    ): QueryShape =
        QueryShape(
            collectionPath = "users/$userId/sync_events",
            filter = "sequence > $lastAppliedSequence",
            orderBy = "sequence",
            limit = limit,
        )

    internal companion object {
        const val DEFAULT_PAGE_SIZE = 200L
        const val UNBOUNDED_LIVE_LISTENER = Long.MAX_VALUE
    }
}

internal interface FirestoreSyncEventQueryGateway {
    suspend fun fetchEvents(queryShape: FirestoreSyncEventFeedClient.QueryShape): List<FirestoreSyncEvent>

    fun listen(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration
}

internal interface FirestoreSyncEventListenerRegistration {
    fun remove()
}

private object ThrowingFirestoreSyncEventQueryGateway : FirestoreSyncEventQueryGateway {
    override suspend fun fetchEvents(queryShape: FirestoreSyncEventFeedClient.QueryShape): List<FirestoreSyncEvent> {
        throw IllegalStateException("FirestoreSyncEventQueryGateway not wired in this context")
    }

    override fun listen(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration {
        throw IllegalStateException("FirestoreSyncEventQueryGateway not wired in this context")
    }
}
