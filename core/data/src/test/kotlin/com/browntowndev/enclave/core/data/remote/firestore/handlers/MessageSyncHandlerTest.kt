@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.enclave.core.data.remote.firestore.FakeEnclaveDatabase
import com.browntowndev.enclave.core.data.remote.firestore.FakeMessageDaoBundle
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Regression tests for the stale same-device echo overwrite bug.
 *
 * **Scenario (the bug):**
 * 1. Generation starts → placeholder written to Room: `messageState=PROCESSING`, `updatedAt=T1`, `pendingSync=1`
 * 2. SyncWorker claims + uploads it → Firestore gets `message_state="processing"`, `updated_at=T1`
 * 3. `markSynced` clears local `pendingSync=0`
 * 4. Generation continues → `persistAllMessageData` writes to Room: `messageState=COMPLETE`,
 *    full content, `updatedAt=T2` (T2 > T1), `pendingSync=1` (dirty, waiting for next SyncWorker)
 * 5. Firestore live listener fires with the PROCESSING echo (step 2 upload arrives back):
 *    same device, `updated_at=T1`
 * 6. `overwriteForSameDeviceEcho` → `overwrite=true` because local `pendingSync=1`
 * 7. **BUG:** `upsertLocal` does a full `@Upsert` with the stale entity (`PROCESSING`, `updatedAt=T1`,
 *    `pendingSync=0`), stomping the newer COMPLETE state and resetting `pendingSync=0` →
 *    the COMPLETE state is now in Room but marked as already-synced, so it **never uploads**.
 *
 * **Expected (post-fix):**
 * A same-device echo whose `updatedAt` is strictly older than the local row must be treated as
 * stale. The local row must not be overwritten — preserving the COMPLETE state and `pendingSync=1`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MessageSyncHandlerTest {
    private val messageDaoBundle = FakeMessageDaoBundle(FakeEnclaveDatabase())
    private val messageDao: MessageDao = messageDaoBundle.messageDao
    private lateinit var handler: MessageSyncHandler

    companion object {
        private const val T1 = 1_000L // upload timestamp
        private const val T2 = 2_000L // completion timestamp (after T1)
        private val MSG_ID = MessageId("msg-abc")
        private val CHAT_ID = ChatId("chat-1")
    }

    @BeforeEach
    fun setup() {
        handler = MessageSyncHandler(createContext(messageDao))
    }

    // -----------------------------------------------------------------------
    // THE BUG: stale same-device echo clobbers local COMPLETE row
    // -----------------------------------------------------------------------

    @Test
    @DisplayName(
        "upsertLocal with overwrite=true and stale echo (T1 < T2) does NOT overwrite local COMPLETE row",
    )
    fun upsertLocal_staleEcho_doesNotOverwriteNewerLocalRow() =
        runTest {
            // Local row: COMPLETE, content written, pendingSync=1, updatedAt=T2
            val localComplete =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "The full generated response.",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 1,
                            updatedAt = T2,
                        ),
                    files = emptyList(),
                )
            messageDaoBundle.stubMessageById(localComplete)

            // Stale echo from Firestore: PROCESSING (clamped to COMPLETE by mapper), updatedAt=T1
            val staleEchoEntity =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE, // mapper already clamped
                            pendingSync = 0,
                            updatedAt = T1, // older than local T2
                        ),
                    files = emptyList(),
                )

            // overwrite=true simulates same-device echo while local pendingSync=1
            handler.upsertLocal(staleEchoEntity, mockk<DocumentSnapshot>(relaxed = true), overwrite = true)

            // The local row must NOT be overwritten — upsert should never be called
            assertTrue(messageDaoBundle.upsertedMessages.isEmpty())
        }

    @Test
    @DisplayName(
        "upsertLocal with overwrite=true and stale echo preserves local pendingSync=1 " +
            "(COMPLETE upload must still fire)",
    )
    fun upsertLocal_staleEcho_preservesPendingSync() =
        runTest {
            val localComplete =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "Full content.",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 1,
                            updatedAt = T2,
                        ),
                    files = emptyList(),
                )
            messageDaoBundle.stubMessageById(localComplete)

            val staleEcho =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 0,
                            updatedAt = T1,
                        ),
                    files = emptyList(),
                )

            handler.upsertLocal(staleEcho, mockk<DocumentSnapshot>(relaxed = true), overwrite = true)

            // Confirm pendingSync was never reset to 0 via an upsert
            assertTrue(messageDaoBundle.upsertedMessages.none { it.pendingSync == 0 })
        }

    // -----------------------------------------------------------------------
    // THE FLICKER: live-generation echo must NOT collapse local placeholder
    //
    // While generation is in flight the local row is in a transient state
    // (PROCESSING/THINKING/GENERATING/ENGINE_LOADING). The listener echo of
    // our own placeholder upload arrives with the same updatedAt but a
    // mapper-clamped COMPLETE state. If the merge runs, the UI briefly flips
    // to a completed AssistantResponse (copy/share row visible) before the
    // next streaming write flips it back — a visible flicker.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName(
        "upsertLocal with overwrite=true and clamped-COMPLETE echo at same updatedAt " +
            "preserves transient local state (no flicker)",
    )
    fun upsertLocal_liveEcho_doesNotCollapseTransientPlaceholder() =
        runTest {
            // Local placeholder mid-generation: PROCESSING, updatedAt=T1
            val localProcessing =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "",
                            role = Role.ASSISTANT,
                            messageState = MessageState.PROCESSING,
                            pendingSync = 0, // already markSynced after upload
                            updatedAt = T1,
                        ),
                    files = emptyList(),
                )
            messageDaoBundle.stubMessageById(localProcessing)

            // Listener echo: mapper has clamped messageState to COMPLETE, same updatedAt
            val clampedEcho =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 0,
                            updatedAt = T1,
                        ),
                    files = emptyList(),
                )

            handler.upsertLocal(clampedEcho, mockk<DocumentSnapshot>(relaxed = true), overwrite = true)

            // Local placeholder must not be touched while it is mid-generation.
            assertTrue(messageDaoBundle.upsertedMessages.isEmpty())
        }

    // -----------------------------------------------------------------------
    // Positive: current-version same-device echo SHOULD still clear pendingSync
    // -----------------------------------------------------------------------

    @Test
    @DisplayName(
        "upsertLocal with overwrite=true and matching updatedAt (current echo) clears pendingSync",
    )
    fun upsertLocal_currentEcho_clearsPendingSync() =
        runTest {
            // Same timestamp — echo matches the upload we sent
            val localDirty =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "content",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 1,
                            updatedAt = T1, // same as echo
                        ),
                    files = emptyList(),
                )
            messageDaoBundle.stubMessageById(localDirty)
            val echoEntity =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "content",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 0,
                            updatedAt = T1, // same — this is our echo
                        ),
                    files = emptyList(),
                )

            handler.upsertLocal(echoEntity, mockk<DocumentSnapshot>(relaxed = true), overwrite = true)

            assertEquals(1, messageDaoBundle.upsertedMessages.size)
            assertEquals(0, messageDaoBundle.upsertedMessages.single().pendingSync)
            assertEquals(MessageState.COMPLETE, messageDaoBundle.upsertedMessages.single().messageState)
        }

    // -----------------------------------------------------------------------
    // Positive: fresh insert (no existing row) always writes
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("upsertLocal with no existing row always inserts the entity")
    fun upsertLocal_noExistingRow_alwaysInserts() =
        runTest {
            messageDaoBundle.stubMessageById(null)
            val incoming =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "new",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 0,
                            updatedAt = T1,
                        ),
                    files = emptyList(),
                )

            handler.upsertLocal(incoming, mockk<DocumentSnapshot>(relaxed = true), overwrite = false)

            assertEquals(1, messageDaoBundle.upsertedMessages.size)
        }

    // -----------------------------------------------------------------------
    // Positive: newer remote row (cross-device or late sync) SHOULD overwrite
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("upsertLocal with newer remote updatedAt overwrites local row")
    fun upsertLocal_newerRemote_overwritesLocal() =
        runTest {
            val localOld =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "old content",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 0,
                            updatedAt = T1,
                        ),
                    files = emptyList(),
                )
            messageDaoBundle.stubMessageById(localOld)
            val newerRemote =
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MSG_ID,
                            chatId = CHAT_ID,
                            content = "newer content from other device",
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            pendingSync = 0,
                            updatedAt = T2, // newer
                        ),
                    files = emptyList(),
                )

            handler.upsertLocal(newerRemote, mockk<DocumentSnapshot>(relaxed = true), overwrite = false)

            assertEquals(1, messageDaoBundle.upsertedMessages.size)
            assertEquals("newer content from other device", messageDaoBundle.upsertedMessages.single().content)
        }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun createContext(messageDao: MessageDao): FirestoreSyncContext =
        testSyncContext(daos = testFirestoreSyncDaos(messageDao = messageDao))
}
