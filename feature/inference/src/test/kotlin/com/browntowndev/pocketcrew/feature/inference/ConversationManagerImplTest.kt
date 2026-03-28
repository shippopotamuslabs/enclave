package com.browntowndev.pocketcrew.feature.inference
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class ConversationManagerImplTest {

    private lateinit var mockEngine: Engine
    private lateinit var mockConversation: Conversation

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockEngine = mockk(relaxed = true)
        mockConversation = mockk(relaxed = true)

        every { mockEngine.isInitialized() } returns false
        every { mockEngine.createConversation(any()) } returns mockConversation
        every { mockConversation.isAlive } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ========== Thread-Safe Engine Lifecycle ==========

    @Test
    fun `getConversation returns ConversationPort wrapper on first call`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)

        // When
        val conversation = manager.getConversation()

        // Then
        assertNotNull(conversation)
        assertTrue(conversation is ConversationPort, "Should return ConversationPort wrapper")
        verify { mockEngine.isInitialized() }
        verify { mockEngine.createConversation(any()) }
    }

    @Test
    fun `getConversation recreates dead conversation`() {
        // Given - conversation exists but is not alive
        every { mockConversation.isAlive } returns false

        val manager = ConversationManagerImpl(engine = mockEngine)
        manager.getConversation() // First call creates conversation

        // When - call again
        val conversation2 = manager.getConversation()

        // Then - should recreate
        assertNotNull(conversation2)
        verify { mockConversation.close() }
    }

    @Test
    fun `closeConversation clears reference`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)
        manager.getConversation()

        // When
        manager.closeConversation()

        // Then
        verify { mockConversation.close() }
    }

    @Test
    fun `closeEngine releases resources`() {
        // Given
        every { mockEngine.isInitialized() } returns true
        val manager = ConversationManagerImpl(engine = mockEngine)
        manager.getConversation()

        // When
        manager.closeEngine()

        // Then
        verify { mockConversation.close() }
        verify { mockEngine.close() }
    }

    @Test
    fun `getConversation reinitializes engine if not initialized`() {
        // Given - engine is not initialized
        every { mockEngine.isInitialized() } returns false

        val manager = ConversationManagerImpl(engine = mockEngine)

        // When
        manager.getConversation()

        // Then
        verify { mockEngine.initialize() }
    }

    @Test
    fun `concurrent access returns same ConversationPort wrapper`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)

        // When - get conversation twice
        val conv1 = manager.getConversation()
        val conv2 = manager.getConversation()

        // Then - should return same wrapper instance (cached)
        assertEquals(conv1, conv2, "Should return same ConversationPort wrapper instance")
    }

    @Test
    fun `getConversation returns different wrapper after closeConversation`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)
        val conv1 = manager.getConversation()

        // When - close and get again
        manager.closeConversation()
        val conv2 = manager.getConversation()

        // Then - should be different wrapper instances
        assertTrue(conv1 !== conv2, "Should return new wrapper after close")
    }

    // ========== Conversation History (setHistory) ==========

    @Test
    fun `setHistory seeds initialMessages in ConversationConfig`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)
        val history = listOf(
            ChatMessage(Role.USER, "Hello, what's up?"),
            ChatMessage(Role.ASSISTANT, "Not much, how are you?")
        )

        // When
        manager.setHistory(history)
        manager.getConversation()

        // Then
        val configSlot = slot<ConversationConfig>()
        verify { mockEngine.createConversation(capture(configSlot)) }

        val initialMessages = configSlot.captured.initialMessages
        assertEquals(2, initialMessages.size)
        // Verify mapping (role and content)
        assertEquals("user", initialMessages[0].role.name.lowercase())
        assertEquals("Hello, what's up?", initialMessages[0].contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text })
        assertEquals("model", initialMessages[1].role.name.lowercase())
        assertEquals("Not much, how are you?", initialMessages[1].contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text })
    }

    @Test
    fun `setting different history invalidates current conversation`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)
        val conv1 = manager.getConversation()

        val history = listOf(ChatMessage(Role.USER, "New history"))

        // When
        manager.setHistory(history)
        val conv2 = manager.getConversation()

        // Then
        assertTrue(conv1 !== conv2, "Should return new conversation instance after history change")
        verify { mockConversation.close() }
    }

    @Test
    fun `setHistory with empty list seeds empty initialMessages`() {
        // Given
        val manager = ConversationManagerImpl(engine = mockEngine)
        val history = emptyList<ChatMessage>()

        // When
        manager.setHistory(history)
        manager.getConversation()

        // Then
        val configSlot = slot<ConversationConfig>()
        verify { mockEngine.createConversation(capture(configSlot)) }
        assertTrue(configSlot.captured.initialMessages.isEmpty())
    }
}
