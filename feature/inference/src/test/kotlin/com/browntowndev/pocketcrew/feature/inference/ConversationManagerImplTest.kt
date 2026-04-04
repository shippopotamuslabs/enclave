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
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
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
    private lateinit var assetFlow: MutableStateFlow<LocalModelAsset?>
    private lateinit var configFlow: MutableStateFlow<LocalModelConfiguration?>
    private var engineInitialized = false

    private val fakeAsset = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = 1L,
            huggingFaceModelName = "test/model",
            remoteFileName = "model.litertlm",
            localFileName = "model.litertlm",
            sha256 = "dummy-hash",
            sizeInBytes = 1000L,
            modelFileFormat = ModelFileFormat.LITERTLM
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
        engineInitialized = false

        // Hot flows that immediately emit values - MutableStateFlow is always active
        assetFlow = MutableStateFlow(fakeAsset)
        configFlow = MutableStateFlow(fakeConfig)

        // Mock Engine constructor
        mockkConstructor(Engine::class)
        every { anyConstructed<Engine>().isInitialized() } answers { engineInitialized }
        every { anyConstructed<Engine>().createConversation(any()) } returns mockConversation
        every { anyConstructed<Engine>().close() } answers { engineInitialized = false }
        every { anyConstructed<Engine>().initialize() } answers { engineInitialized = true }

        every { mockConversation.isAlive } returns true
        every { mockConversation.close() } returns Unit

        // Mock file operations for model path
        val mockFilesDir = File("/fake/files/dir")
        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

        // Model registry mocks
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns fakeAsset
        coEvery { mockModelRegistry.getRegisteredConfiguration(any()) } returns fakeConfig
        every { mockModelRegistry.observeAsset(any()) } returns assetFlow.asStateFlow()
        every { mockModelRegistry.observeConfiguration(any()) } returns configFlow.asStateFlow()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkConstructor(Engine::class)
    }

    // ========== Thread-Safe Engine Lifecycle ==========

    @Test
    fun `getConversation returns ConversationPort wrapper on first call`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)

        // When
        val conversation = manager.getConversation(ModelType.MAIN)

        // Then
        assertNotNull(conversation)
        assertTrue(conversation is ConversationPort, "Should return ConversationPort wrapper")
        verify { anyConstructed<Engine>().createConversation(any()) }
    }

    @Test
    fun `getConversation recreates dead conversation`() = runTest {
        // Given - conversation exists but is not alive
        every { mockConversation.isAlive } returns false

        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        manager.getConversation(ModelType.MAIN) // First call creates conversation

        // When - call again
        val conversation2 = manager.getConversation(ModelType.MAIN)

        // Then - should recreate
        assertNotNull(conversation2)
        verify { mockConversation.close() }
    }

    @Test
    fun `closeConversation clears reference`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        manager.getConversation(ModelType.MAIN)

        // When
        manager.closeConversation()

        // Then
        verify { mockConversation.close() }
    }

    @Test
    fun `closeEngine releases resources`() = runTest {
        // Given
        every { anyConstructed<Engine>().isInitialized() } returns true
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        manager.getConversation(ModelType.MAIN)

        // When
        manager.closeEngine()

        // Then
        verify { mockConversation.close() }
        verify { anyConstructed<Engine>().close() }
    }

    @Test
    fun `concurrent access returns same ConversationPort wrapper`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)

        // When - get conversation twice
        val conv1 = manager.getConversation(ModelType.MAIN)
        val conv2 = manager.getConversation(ModelType.MAIN)

        // Then - should return same wrapper instance (cached)
        assertEquals(conv1, conv2, "Should return same ConversationPort wrapper instance")
    }

    @Test
    fun `getConversation returns different wrapper after closeConversation`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val conv1 = manager.getConversation(ModelType.MAIN)

        // When - close and get again
        manager.closeConversation()
        val conv2 = manager.getConversation(ModelType.MAIN)

        // Then - should be different wrapper instances
        assertTrue(conv1 !== conv2, "Should return new wrapper after close")
    }

    // ========== Conversation History (setHistory) ==========

    @Test
    fun `setHistory seeds initialMessages in ConversationConfig`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val history = listOf(
            ChatMessage(Role.USER, "Hello, what's up?"),
            ChatMessage(Role.ASSISTANT, "Not much, how are you?")
        )

        // When
        manager.setHistory(history)
        manager.getConversation(ModelType.MAIN)

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
    fun `setting continuation history preserves conversation`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val history1 = listOf(ChatMessage(Role.USER, "Part 1"))
        manager.setHistory(history1)
        val conv1 = manager.getConversation(ModelType.MAIN)

        val history2 = listOf(ChatMessage(Role.USER, "Part 1"), ChatMessage(Role.ASSISTANT, "Response 1"))

        // When
        manager.setHistory(history2)
        val conv2 = manager.getConversation(ModelType.MAIN)

        // Then
        assertTrue(conv1 === conv2, "Should reuse conversation instance when history is a continuation")
        verify(exactly = 0) { mockConversation.close() }
    }

    @Test
    fun `setting different history invalidates current conversation`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val conv1 = manager.getConversation(ModelType.MAIN)

        val history = listOf(ChatMessage(Role.USER, "New history"))

        // When
        manager.setHistory(history)
        val conv2 = manager.getConversation(ModelType.MAIN)

        // Then
        assertTrue(conv1 !== conv2, "Should return new conversation instance after history change")
        verify { mockConversation.close() }
    }

    @Test
    fun `setHistory with empty list seeds empty initialMessages`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val history = emptyList<ChatMessage>()

        // When
        manager.setHistory(history)
        manager.getConversation(ModelType.MAIN)

        // Then
        val configSlot = slot<ConversationConfig>()
        verify { anyConstructed<Engine>().createConversation(capture(configSlot)) }
        assertTrue(configSlot.captured.initialMessages.isEmpty())
    }

    @Test
    fun `getConversation recreates conversation when modelType switches but reuses engine and preserves history`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val history = listOf(
            ChatMessage(Role.USER, "Remember this"),
            ChatMessage(Role.ASSISTANT, "I remember it")
        )

        val fastConfig = fakeConfig.copy(
            id = 10L,
            displayName = "Fast",
            temperature = 0.3,
            topK = 20,
            topP = 0.8,
            thinkingEnabled = false,
            systemPrompt = "FAST SYSTEM"
        )
        val thinkingConfig = fakeConfig.copy(
            id = 20L,
            displayName = "Thinking",
            temperature = 0.9,
            topK = 60,
            topP = 0.95,
            thinkingEnabled = true,
            systemPrompt = "THINKING SYSTEM"
        )

        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns fakeAsset
        coEvery { mockModelRegistry.getRegisteredConfiguration(ModelType.FAST) } returns fastConfig
        coEvery { mockModelRegistry.getRegisteredConfiguration(ModelType.THINKING) } returns thinkingConfig

        val configs = mutableListOf<ConversationConfig>()
        every { anyConstructed<Engine>().createConversation(capture(configs)) } returns mockConversation

        manager.setHistory(history)

        // When
        val fastConversation = manager.getConversation(
            ModelType.FAST,
            com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions(
                reasoningBudget = 0,
                modelType = ModelType.FAST
            )
        )
        val thinkingConversation = manager.getConversation(
            ModelType.THINKING,
            com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions(
                reasoningBudget = 2048,
                modelType = ModelType.THINKING
            )
        )

        // Then
        assertTrue(fastConversation !== thinkingConversation, "Should recreate conversation when ModelType changes")
        verify(exactly = 1) { anyConstructed<Engine>().initialize() }
        verify(exactly = 1) { mockConversation.close() }
        verify(exactly = 2) { anyConstructed<Engine>().createConversation(any()) }
        assertEquals(2, configs.size)
        assertEquals("FAST SYSTEM", extractText(configs[0].systemInstruction))
        assertEquals("THINKING SYSTEM", extractText(configs[1].systemInstruction))
        assertEquals(2, configs[1].initialMessages.size)
        assertEquals("Remember this", extractText(configs[1].initialMessages[0]))
        assertEquals("I remember it", extractText(configs[1].initialMessages[1]))
    }

    @Test
    fun `getConversation uses fast prompt on first turn`() = runTest {
        // Given
        val manager = ConversationManagerImpl(mockContext, mockModelRegistry)
        val fastConfig = fakeConfig.copy(
            id = 101L,
            displayName = "Fast",
            thinkingEnabled = false,
            systemPrompt = "FAST PROMPT"
        )
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns fakeAsset
        coEvery { mockModelRegistry.getRegisteredConfiguration(ModelType.FAST) } returns fastConfig

        val configs = mutableListOf<ConversationConfig>()
        every { anyConstructed<Engine>().createConversation(capture(configs)) } returns mockConversation

        // When
        manager.getConversation(
            ModelType.FAST,
            com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions(
                reasoningBudget = 0,
                modelType = ModelType.FAST
            )
        )

        // Then
        assertEquals(1, configs.size)
        assertEquals("FAST PROMPT", extractText(configs[0].systemInstruction))
    }

    private fun extractText(contents: Contents?): String {
        if (contents == null) return ""
        return contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
    }

    private fun extractText(message: Message?): String {
        if (message == null) return ""
        return extractText(message.contents)
    }
}
