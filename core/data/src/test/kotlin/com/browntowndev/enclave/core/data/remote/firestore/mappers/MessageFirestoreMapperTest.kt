@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore.mappers

import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Tests for [MessageFirestoreMapper].
 *
 * The most important invariant: any non-COMPLETE [MessageState] stored in Firestore must be
 * clamped to COMPLETE when mapped back to a local [MessageEntity].  Transient generation
 * states (PROCESSING, THINKING, GENERATING, ENGINE_LOADING) are written during live
 * inference and are meaningless once that session ends.  Restoring them as-is causes the
 * InputBar to show the "stop" icon on existing chats opened in a new session.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MessageFirestoreMapperTest {
    private lateinit var ctx: FirestoreSyncContext

    @BeforeEach
    fun setup() {
        ctx = testSyncContext()
    }

    // -----------------------------------------------------------------------
    // Null / missing chat_id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fromDocument returns null when chat_id is missing")
    fun fromDocument_returnsNull_whenChatIdMissing() {
        val doc = mockDoc(id = "msg-1", chatId = null)
        assertNull(MessageFirestoreMapper.fromDocument(ctx, doc))
    }

    @Test
    @DisplayName("fromDocument returns null when chat_id is blank")
    fun fromDocument_returnsNull_whenChatIdBlank() {
        val doc = mockDoc(id = "msg-1", chatId = "")
        assertNull(MessageFirestoreMapper.fromDocument(ctx, doc))
    }

    // -----------------------------------------------------------------------
    // State clamping — regression guard for the stop-icon bug
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "messageState=''{0}'' is clamped to COMPLETE on inbound sync")
    @ValueSource(strings = ["processing", "thinking", "generating", "engine_loading"])
    @DisplayName("Non-COMPLETE states are clamped to COMPLETE at the Firestore boundary")
    fun fromDocument_clampsNonCompleteStatesToComplete(rawState: String) {
        val doc = mockDoc(id = "msg-2", chatId = "chat-1", messageState = rawState)
        val entity = MessageFirestoreMapper.fromDocument(ctx, doc)
        assertNotNull(entity)
        assertEquals(
            MessageState.COMPLETE,
            entity!!.messageState,
            "Expected COMPLETE for state='$rawState' but got ${entity.messageState}",
        )
    }

    @Test
    @DisplayName("COMPLETE state is preserved as COMPLETE")
    fun fromDocument_preservesCompleteState() {
        val doc = mockDoc(id = "msg-3", chatId = "chat-1", messageState = "complete")
        val entity = MessageFirestoreMapper.fromDocument(ctx, doc)
        assertNotNull(entity)
        assertEquals(MessageState.COMPLETE, entity!!.messageState)
    }

    @Test
    @DisplayName("Missing message_state defaults to COMPLETE")
    fun fromDocument_defaultsToComplete_whenStateAbsent() {
        val doc = mockDoc(id = "msg-4", chatId = "chat-1", messageState = null)
        val entity = MessageFirestoreMapper.fromDocument(ctx, doc)
        assertNotNull(entity)
        assertEquals(MessageState.COMPLETE, entity!!.messageState)
    }

    @Test
    @DisplayName("Unknown message_state string defaults to COMPLETE")
    fun fromDocument_defaultsToComplete_forUnknownStateString() {
        val doc = mockDoc(id = "msg-5", chatId = "chat-1", messageState = "completely_made_up_state")
        val entity = MessageFirestoreMapper.fromDocument(ctx, doc)
        assertNotNull(entity)
        assertEquals(MessageState.COMPLETE, entity!!.messageState)
    }

    // -----------------------------------------------------------------------
    // Basic field mapping
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fromDocument maps id and chatId correctly")
    fun fromDocument_mapsIdAndChatId() {
        val doc = mockDoc(id = "msg-99", chatId = "chat-42")
        val entity = MessageFirestoreMapper.fromDocument(ctx, doc)
        assertNotNull(entity)
        assertEquals("msg-99", entity!!.id.value)
        assertEquals("chat-42", entity.chatId.value)
    }

    @Test
    @DisplayName("pendingSync is always 0 for inbound sync entities")
    fun fromDocument_setPendingSyncToZero() {
        val doc = mockDoc(id = "msg-10", chatId = "chat-1")
        val entity = MessageFirestoreMapper.fromDocument(ctx, doc)
        assertNotNull(entity)
        assertEquals(0, entity!!.pendingSync)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun mockDoc(
        id: String,
        chatId: String?,
        messageState: String? = "complete",
    ): DocumentSnapshot =
        mockk<DocumentSnapshot>(relaxed = true) {
            every { this@mockk.id } returns id
            every { getString("chat_id") } returns chatId
            every { getString("message_state") } returns messageState
            every { getString("role") } returns "assistant"
            every { getLong("created_at") } returns 0L
            every { getLong("updated_at") } returns 0L
            every { getBoolean("deleted") } returns false
        }
}
