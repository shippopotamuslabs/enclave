package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File


class ConversationManagerImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockEngine: Engine
    private lateinit var mockConversation: Conversation
    private lateinit var testScope: CoroutineScope
    private lateinit var assetFlow: MutableStateFlow<LocalModelAsset?>
    private lateinit var configFlow: MutableStateFlow<LocalModelConfiguration?>

    private val fakeAsset = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = 1L,
            huggingFaceModelName = "test/model",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = "dummy-hash",
            sizeInBytes = 1000L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        configurations = emptyList()
    )

    private val fakeConfig = LocalModelConfiguration(
        id = 1L,
        localModelId = 1L,
        displayName = "Test Config",
        temperature = 0.7,
        topK = 40,
        topP = 0.9,
        minP = 0.1,
        repetitionPenalty = 1.1,
        maxTokens = 4096,
        contextWindow = 4096,
        thinkingEnabled = false,
        systemPrompt = "You are a helpful assistant.",
        isSystemPreset = true
    )

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockEngine = mockk(relaxed = true)
        mockConversation = mockk(relaxed = true)

        // Test scope uses Unconfined to run flow collection synchronously
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        // Hot flows that immediately emit values - MutableStateFlow is always active
        assetFlow = MutableStateFlow(fakeAsset)
        configFlow = MutableStateFlow(fakeConfig)

        // Mock Engine constructor
        mockkConstructor(Engine::class)
        every { anyConstructed<Engine>().isInitialized() } returns false
        every { anyConstructed<Engine>().createConversation(any()) } returns mockConversation
        every { anyConstructed<Engine>().close() } returns Unit
        every { anyConstructed<Engine>().initialize() } returns Unit

        every { mockConversation.isAlive } returns true
        every { mockConversation.close() } returns Unit

        // Mock file operations for model path
        val mockFilesDir = File("/fake/files/dir")
        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

        // Model registry flows - hot StateFlows that always have a value
        every { mockModelRegistry.observeAsset(ModelType.MAIN) } returns assetFlow.asStateFlow()
        every { mockModelRegistry.observeConfiguration(ModelType.MAIN) } returns configFlow.asStateFlow()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkConstructor(Engine::class)
    }

    // ========== Thread-Safe Engine Lifecycle ==========

    @Test
    fun `getConversation returns ConversationPort wrapper on first call`() {
        // Given
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)

        // When
        val conversation = manager.getConversation()

        // Then
        assertNotNull(conversation)
        assertTrue(conversation is ConversationPort, "Should return ConversationPort wrapper")
        verify { anyConstructed<Engine>().createConversation(any()) }
    }

    @Test
    fun `getConversation recreates dead conversation`() {
        // Given - conversation exists but is not alive
        every { mockConversation.isAlive } returns false

        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
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
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
        manager.getConversation()

        // When
        manager.closeConversation()

        // Then
        verify { mockConversation.close() }
    }

    @Test
    fun `closeEngine releases resources`() {
        // Given
        every { anyConstructed<Engine>().isInitialized() } returns true
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
        manager.getConversation()

        // When
        manager.closeEngine()

        // Then
        verify { mockConversation.close() }
        verify { anyConstructed<Engine>().close() }
    }

    @Test
    fun `concurrent access returns same ConversationPort wrapper`() {
        // Given
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)

        // When - get conversation twice
        val conv1 = manager.getConversation()
        val conv2 = manager.getConversation()

        // Then - should return same wrapper instance (cached)
        assertEquals(conv1, conv2, "Should return same ConversationPort wrapper instance")
    }

    @Test
    fun `getConversation returns different wrapper after closeConversation`() {
        // Given
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
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
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
        val history = listOf(
            ChatMessage(Role.USER, "Hello, what's up?"),
            ChatMessage(Role.ASSISTANT, "Not much, how are you?")
        )

        // When
        manager.setHistory(history)
        manager.getConversation()

        // Then
        val configSlot = slot<ConversationConfig>()
        verify { anyConstructed<Engine>().createConversation(capture(configSlot)) }

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
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
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
        val manager = ConversationManagerImpl(mockContext, ModelType.MAIN, mockModelRegistry, testScope)
        val history = emptyList<ChatMessage>()

        // When
        manager.setHistory(history)
        manager.getConversation()

        // Then
        val configSlot = slot<ConversationConfig>()
        verify { anyConstructed<Engine>().createConversation(capture(configSlot)) }
        assertTrue(configSlot.captured.initialMessages.isEmpty())
    }
}
