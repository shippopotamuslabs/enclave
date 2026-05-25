package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirestoreSyncHydrationRecoveryTest {
    private val chatDao = mockk<ChatDao>()
    private val syncStateStore = HydrationRecoveryTestSyncStateStore(sequence = 42L)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `resets cursor when Room has no chats but watermark is ahead`() =
        runTest {
            coEvery { chatDao.countActiveChats() } returns 0
            val recovery =
                FirestoreSyncHydrationRecovery(
                    chatDao = chatDao,
                    syncStateStore = syncStateStore,
                    loggingPort = loggingPort,
                )

            assertTrue(recovery.resetStaleCursorIfLocalStoreEmpty("alice"))

            assertEquals(0L, syncStateStore.lastAppliedSequence("alice"))
            verify { loggingPort.warning(any(), any()) }
        }

    @Test
    fun `does not reset when chats exist locally`() =
        runTest {
            coEvery { chatDao.countActiveChats() } returns 3
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
            coEvery { chatDao.countActiveChats() } returns 0
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
