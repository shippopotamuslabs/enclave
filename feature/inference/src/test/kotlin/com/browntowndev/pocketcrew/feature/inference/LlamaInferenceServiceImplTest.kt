package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LlamaInferenceServiceImplTest {

    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var loggingPort: LoggingPort
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var context: Context
    private lateinit var toolExecutor: ToolExecutorPort

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        sessionManager = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        localModelRepository = mockk()
        activeModelProvider = mockk()
        context = mockk(relaxed = true)
        toolExecutor = mockk()

        every { context.getExternalFilesDir(null) } returns File("/tmp")

        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("7"),
            isLocal = true,
            name = "Llama Fast",
            systemPrompt = "You are a helpful assistant.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("7")) } returns LocalModelAsset(
            metadata = LocalModelMetadata(
                id = 99L,
                huggingFaceModelName = "test/llama",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "abc123",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
            ),
            configurations = emptyList(),
        )
        coEvery { sessionManager.initializeEngine(any()) } returns Unit
        coEvery { sessionManager.setHistory(any()) } returns Unit
        coEvery { sessionManager.startNewConversation() } returns Unit
        coEvery { sessionManager.clearConversation() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `sendPrompt hides llama tool envelope and resumes the same session with tool result`() = runTest {
        coEvery { sessionManager.sendUserMessage(any()) } returns Unit
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returnsMany listOf(
            flowOf(
                GenerationEvent.Completed(
                    """<think>Need to search first.</think><tool_call>{"name":"tavily_web_search","arguments":{"query":"latest android tool calling"}}</tool_call>"""
                )
            ),
            flowOf(
                GenerationEvent.Completed(
                    "<think>Reviewing search result.</think>Use the search result summary."
                )
            )
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = createService()

        val events = service.sendPrompt(
            prompt = "Find recent Android tool calling news",
            options = searchEnabledOptions(),
            closeConversation = false,
        ).toList()

        assertEquals(
            listOf("Use the search result summary."),
            events.filterIsInstance<InferenceEvent.PartialResponse>().map(InferenceEvent.PartialResponse::chunk)
        )
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to search first." })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Reviewing search result." })
        assertTrue(events.last() is InferenceEvent.Finished)
        coVerify(exactly = 2) { sessionManager.sendUserMessage(any()) }
        coVerify(exactly = 1) {
            sessionManager.sendUserMessage(
                "<tool_result>{\"query\":\"latest android tool calling\",\"results\":[{\"url\":\"https://example.invalid/stub\"}]}</tool_result>"
            )
        }
        coVerify(exactly = 1) { toolExecutor.execute(any()) }
    }

    @Test
    fun `sendPrompt surfaces malformed llama tool envelope as typed failure`() = runTest {
        coEvery { sessionManager.sendUserMessage(any()) } returns Unit
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns flowOf(
            GenerationEvent.Completed("""<tool_call>{"name":"tavily_web_search","arguments":""")
        )

        val service = createService()

        val events = service.sendPrompt(
            prompt = "Find recent Android tool calling news",
            options = searchEnabledOptions(),
            closeConversation = false,
        ).toList()

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Malformed tool_call envelope", error.cause.message)
    }

    @Test
    fun `sendPrompt wraps llama replay failure as typed resume error`() = runTest {
        coEvery { sessionManager.sendUserMessage(any()) } returns Unit
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returnsMany listOf(
            flowOf(
                GenerationEvent.Completed(
                    """<tool_call>{"name":"tavily_web_search","arguments":{"query":"latest android tool calling"}}</tool_call>"""
                )
            ),
            flowOf(GenerationEvent.Error(RuntimeException("resume boom")))
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = createService()

        val events = service.sendPrompt(
            prompt = "Find recent Android tool calling news",
            options = searchEnabledOptions(),
            closeConversation = false,
        ).toList()

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Failed to resume llama generation after tool replay", error.cause.message)
    }

    private fun createService(): LlamaInferenceServiceImpl =
        LlamaInferenceServiceImpl(
            sessionManager = sessionManager,
            processThinkingTokens = ProcessThinkingTokensUseCase(),
            loggingPort = loggingPort,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            context = context,
            modelType = ModelType.FAST,
            toolExecutor = toolExecutor,
        )

    private fun searchEnabledOptions(): GenerationOptions =
        GenerationOptions(
            reasoningBudget = 0,
            modelType = ModelType.FAST,
            systemPrompt = "Use tools when needed.\n<tool_call>{\"name\":\"tavily_web_search\",\"arguments\":{\"query\":\"...\"}}</tool_call>",
        )
}
