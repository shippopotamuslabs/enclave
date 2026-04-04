package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.feature.inference.LlamaInferenceServiceImpl
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for LlamaInferenceServiceImpl focusing on [THINK] tag handling.
 * These tests verify that the service correctly:
 * - Emits InferenceEvent.Thinking for [THINK] content
 * - Strips [THINK] and [/THINK] tags from output
 * - Handles partial tokens streaming correctly
 * - Multiple [THINK] blocks are all classified as thinking
 */
class LlamaInferenceServiceImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockEngine: LlamaEnginePort
    private lateinit var mockLoggingPort: LoggingPort
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase
    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var service: LlamaInferenceServiceImpl

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

        mockContext = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockEngine = mockk(relaxed = true)
        mockLoggingPort = mockk(relaxed = true)

        val mockFilesDir = java.io.File("/fake/files/dir")
        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

        // Mock model registry to return valid asset and config
        // Note: mockModelRegistry is relaxed=true, so suspend functions return null by default
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns fakeAsset
        coEvery { mockModelRegistry.getRegisteredConfiguration(ModelType.MAIN) } returns fakeConfig

        // Create real ProcessThinkingTokensUseCase
        processThinkingTokens = ProcessThinkingTokensUseCase()

        // Create session manager with mock engine
        sessionManager = LlamaChatSessionManager(mockEngine)

        // Create service with DI-style constructor
        service = LlamaInferenceServiceImpl(
            sessionManager = sessionManager,
            processThinkingTokens = processThinkingTokens,
            loggingPort = mockLoggingPort,
            modelRegistry = mockModelRegistry,
            context = mockContext,
            modelType = ModelType.FAST
        )
    }

    private fun createService() = LlamaInferenceServiceImpl(
        sessionManager = sessionManager,
        processThinkingTokens = processThinkingTokens,
        loggingPort = mockLoggingPort,
        modelRegistry = mockModelRegistry,
        context = mockContext,
        modelType = ModelType.FAST
    )

    // =========================================================================
    // [THINK] Tag Tests (Streaming & Partial Token Handling)
    // =========================================================================

    @Test
    fun `service emits InferenceEvent_Thinking for THINK_bracket content`() = runTest {
        // Given - tokens arrive separately (streaming)
        val token1 = GenerationEvent.Token("[THINK]")
        val token2 = GenerationEvent.Token("thinking...")
        val token3 = GenerationEvent.Token("[/THINK]answer")
        val token4 = GenerationEvent.Completed("")

        every { mockEngine.generateWithOptions(any()) } returns flowOf(token1, token2, token3, token4)

        // When
        val events = mutableListOf<InferenceEvent>()
        service.sendPrompt("Hello", closeConversation = false).collect { event ->
            events.add(event)
        }

        // Then - should emit Thinking events for content inside [THINK] tags
        val thinkingEvents = events.filterIsInstance<InferenceEvent.Thinking>()
        val partialResponseEvents = events.filterIsInstance<InferenceEvent.PartialResponse>()

        // thinking... should be classified as thinking
        assert(thinkingEvents.any { it.chunk == "thinking..." }) {
            "Expected 'thinking...' to be emitted as InferenceEvent.Thinking"
        }

        // answer should be classified as visible (partial response)
        assert(partialResponseEvents.any { it.chunk == "answer" }) {
            "Expected 'answer' to be emitted as InferenceEvent.PartialResponse"
        }

        // Tags themselves should NOT appear in output
        assert(events.none { it is InferenceEvent.Thinking && it.chunk.contains("[THINK]") }) {
            "[THINK] tag should be stripped from output"
        }
        assert(events.none { it is InferenceEvent.PartialResponse && it.chunk.contains("[/THINK]") }) {
            "[/THINK] tag should be stripped from output"
        }
    }

    @Test
    fun `service handles partial tokens streaming`() = runTest {
        // Given - token split across chunks (simulating streaming boundaries)
        val token1 = GenerationEvent.Token("[TH")
        val token2 = GenerationEvent.Token("INK]")
        val token3 = GenerationEvent.Token("thinking")
        val token4 = GenerationEvent.Token("[/TH")
        val token5 = GenerationEvent.Token("INK]")
        val token6 = GenerationEvent.Token("answer")
        val token7 = GenerationEvent.Completed("")

        every { mockEngine.generateWithOptions(any()) } returns flowOf(token1, token2, token3, token4, token5, token6, token7)

        // When
        val events = mutableListOf<InferenceEvent>()
        service.sendPrompt("Hello", closeConversation = false).collect { event ->
            events.add(event)
        }

        // Then - partial tokens should be handled correctly
        // The content should still be classified as thinking
        val thinkingEvents = events.filterIsInstance<InferenceEvent.Thinking>()
        val allThinkingText = thinkingEvents.joinToString("") { it.chunk }

        assert(allThinkingText.contains("thinking")) {
            "Expected 'thinking' to appear in thinking events, got: $allThinkingText"
        }
    }

    @Test
    fun `multiple THINK_bracket blocks are all classified as thinking`() = runTest {
        // Given - multiple thinking blocks
        val token1 = GenerationEvent.Token("[THINK]part1[/THINK]intermediate[THINK]part2[/THINK]final")
        val token2 = GenerationEvent.Completed("")

        every { mockEngine.generateWithOptions(any()) } returns flowOf(token1, token2)

        // When
        val events = mutableListOf<InferenceEvent>()
        service.sendPrompt("Hello", closeConversation = false).collect { event ->
            events.add(event)
        }

        // Then - both thinking blocks should be classified as thinking
        val thinkingEvents = events.filterIsInstance<InferenceEvent.Thinking>()
        val partialResponseEvents = events.filterIsInstance<InferenceEvent.PartialResponse>()
        val allThinkingText = thinkingEvents.joinToString("") { it.chunk }
        val allVisibleText = partialResponseEvents.joinToString("") { it.chunk }

        assert(allThinkingText.contains("part1")) {
            "Expected 'part1' to be in thinking text, got: $allThinkingText"
        }
        assert(allThinkingText.contains("part2")) {
            "Expected 'part2' to be in thinking text, got: $allThinkingText"
        }
        assert(allVisibleText.contains("intermediate")) {
            "Expected 'intermediate' to be in visible text, got: $allVisibleText"
        }
        assert(allVisibleText.contains("final")) {
            "Expected 'final' to be in visible text, got: $allVisibleText"
        }
    }

    @Test
    fun `service emits InferenceEvent_Finished after all tokens`() = runTest {
        // Given
        val token1 = GenerationEvent.Token("hello")
        val token2 = GenerationEvent.Completed("")

        every { mockEngine.generateWithOptions(any()) } returns flowOf(token1, token2)

        // When
        val events = mutableListOf<InferenceEvent>()
        service.sendPrompt("Hello", closeConversation = false).collect { event ->
            events.add(event)
        }

        // Then
        assert(events.any { it is InferenceEvent.Finished }) {
            "Expected InferenceEvent.Finished to be emitted"
        }
    }

    @Test
    fun `angle bracket format still works correctly`() = runTest {
        // Given - tokens arrive separately (streaming)
        val token1 = GenerationEvent.Token("<think>")
        val token2 = GenerationEvent.Token("thinking...")
        val token3 = GenerationEvent.Token("</think>answer")
        val token4 = GenerationEvent.Completed("")

        every { mockEngine.generateWithOptions(any()) } returns flowOf(token1, token2, token3, token4)

        // When
        val events = mutableListOf<InferenceEvent>()
        service.sendPrompt("Hello", closeConversation = false).collect { event ->
            events.add(event)
        }

        // Then
        val thinkingEvents = events.filterIsInstance<InferenceEvent.Thinking>()
        val partialResponseEvents = events.filterIsInstance<InferenceEvent.PartialResponse>()

        assert(thinkingEvents.any { it.chunk == "thinking..." }) {
            "Expected 'thinking...' to be Thinking"
        }
        assert(partialResponseEvents.any { it.chunk == "answer" }) {
            "Expected 'answer' to be PartialResponse"
        }
    }
}
