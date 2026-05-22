package com.browntowndev.enclave.core.data.remote.firestore

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FirestoreSyncEventFeedClientTest {
    @Test
    fun `SCN-ANDROID-003 feed client query uses sequence greater than last applied sequence`() =
        runTest {
            val gateway = RecordingFeedQueryGateway()
            val client = FirestoreSyncEventFeedClient(gateway = gateway)

            client.fetchNextPage(userId = "alice", lastAppliedSequence = 42)

            val queryShape = requireNotNull(gateway.lastFetchQuery)
            assertEquals("users/alice/sync_events", queryShape.collectionPath)
            assertEquals("sequence > 42", queryShape.filter)
            assertEquals("sequence", queryShape.orderBy)
            assertEquals(200, queryShape.limit)
        }

    @Test
    fun `live listener query is unbounded so events beyond first replay page are not missed`() =
        runTest {
            val gateway = RecordingFeedQueryGateway()
            val client = FirestoreSyncEventFeedClient(gateway = gateway)

            client.attachLiveListener(userId = "alice", lastAppliedSequence = 42) { }

            val queryShape = requireNotNull(gateway.lastListenQuery)
            assertEquals("users/alice/sync_events", queryShape.collectionPath)
            assertEquals("sequence > 42", queryShape.filter)
            assertEquals("sequence", queryShape.orderBy)
            assertEquals(Long.MAX_VALUE, queryShape.limit)
        }
}

private class RecordingFeedQueryGateway : FirestoreSyncEventQueryGateway {
    var lastFetchQuery: FirestoreSyncEventFeedClient.QueryShape? = null
    var lastListenQuery: FirestoreSyncEventFeedClient.QueryShape? = null

    override suspend fun fetchEvents(queryShape: FirestoreSyncEventFeedClient.QueryShape): List<FirestoreSyncEvent> {
        lastFetchQuery = queryShape
        return emptyList()
    }

    override fun listen(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration {
        lastListenQuery = queryShape
        return object : FirestoreSyncEventListenerRegistration {
            override fun remove() = Unit
        }
    }
}
