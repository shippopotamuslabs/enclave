package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class FirestoreSyncHydrationRecoveryTest {
    private val chatDao = FakeChatDao()
    private val syncStateStore = HydrationRecoveryTestSyncStateStore(sequence = 42L)
    private val loggingPort = FakeLoggingPort()

    @Test
    fun `resets cursor when Room has no chats but watermark is ahead`() =
        runTest {
            val recovery =
                FirestoreSyncHydrationRecovery(
                    chatDao = chatDao,
                    syncStateStore = syncStateStore,
                    loggingPort = loggingPort,
                )

            assertTrue(recovery.resetStaleCursorIfLocalStoreEmpty("alice"))

            assertEquals(0L, syncStateStore.lastAppliedSequence("alice"))
            assertTrue(loggingPort.warningCalls.isNotEmpty())
        }

    @Test
    fun `does not reset when chats exist locally`() =
        runTest {
            repeat(3) { index ->
                chatDao.seed(
                    ChatEntity(
                        id = ChatId("chat-$index"),
                        name = "Chat $index",
                        created = Date(0),
                        lastModified = Date(0),
                        pinned = false,
                    ),
                )
            }
            val recovery =
                FirestoreSyncHydrationRecovery(
                    chatDao = chatDao,
                    syncStateStore = syncStateStore,
                    loggingPort = loggingPort,
                )

            assertFalse(recovery.resetStaleCursorIfLocalStoreEmpty("alice"))

            assertEquals(42L, syncStateStore.lastAppliedSequence("alice"))
        }

    @Test
    fun `does not reset when watermark is already zero`() =
        runTest {
            syncStateStore.setLastAppliedSequence("alice", 0L)
            val recovery =
                FirestoreSyncHydrationRecovery(
                    chatDao = chatDao,
                    syncStateStore = syncStateStore,
                    loggingPort = loggingPort,
                )

            assertFalse(recovery.resetStaleCursorIfLocalStoreEmpty("alice"))
        }
}

private class HydrationRecoveryTestSyncStateStore(
    private var sequence: Long,
) : FirestoreSyncStateStore() {
    override suspend fun lastAppliedSequence(userId: String): Long = sequence

    override suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        this.sequence = sequence
    }
}
