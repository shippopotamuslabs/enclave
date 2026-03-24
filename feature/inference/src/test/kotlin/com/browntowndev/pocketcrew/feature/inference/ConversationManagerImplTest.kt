package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.google.ai.edge.litertlm.Engine
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class ConversationManagerImplTest {

    private lateinit var mockEngine: Engine
    private lateinit var mockConversation: com.google.ai.edge.litertlm.Conversation

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
}
