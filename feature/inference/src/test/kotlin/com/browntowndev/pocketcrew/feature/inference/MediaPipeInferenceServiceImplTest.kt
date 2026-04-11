package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.common.util.concurrent.Futures
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaPipeInferenceServiceImplTest {

    private lateinit var llmInference: LlmInferenceWrapper
    private lateinit var session: LlmSessionPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var toolExecutor: ToolExecutorPort
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0

        llmInference = mockk()
        session = mockk(relaxed = true)
        activeModelProvider = mockk()
        toolExecutor = mockk()
        context = mockk(relaxed = true)

        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("7"),
            isLocal = true,
            name = "MediaPipe Fast",
            systemPrompt = "Be concise.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        every { llmInference.createSession(any()) } returns session
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `sendPrompt hides MediaPipe tool envelope and emits only the final assistant text`() = runTest {
        val chunks = ArrayDeque(
            listOf(
                """<think>Need to search first.</think><tool_call>{"name":"tavily_web_search","arguments":{"query":"best folding phones 2026"}}</tool_call>""",
                "<think>Reviewing search result.</think>Use the search result summary.",
            )
        )
        every { session.generateResponseAsync(any()) } answers {
            val listener = firstArg<ProgressListener<String>>()
            val chunk = chunks.removeFirst()
            listener.run(chunk, true)
            Futures.immediateFuture(chunk)
        }
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"best folding phones 2026","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = MediaPipeInferenceServiceImpl(
            llmInference = llmInference,
            context = context,
            modelType = ModelType.FAST,
            activeModelProvider = activeModelProvider,
            processThinkingTokens = ProcessThinkingTokensUseCase(),
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "What are the best folding phones?",
            options = GenerationOptions(
                reasoningBudget = 0,
                modelType = ModelType.FAST,
                systemPrompt = "Use tools when needed.\n<tool_call>{\"name\":\"tavily_web_search\",\"arguments\":{\"query\":\"...\"}}</tool_call>",
            ),
            closeConversation = false,
        ).toList()

        assertEquals(
            listOf("Use the search result summary."),
            events.filterIsInstance<InferenceEvent.PartialResponse>().map(InferenceEvent.PartialResponse::chunk)
        )
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to search first." })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Reviewing search result." })
        assertTrue(events.last() is InferenceEvent.Finished)
        verify(exactly = 2) { session.addQueryChunk(any()) }
    }

    @Test
    fun `sendPrompt surfaces malformed local envelope as a typed failure`() = runTest {
        every { session.generateResponseAsync(any()) } answers {
            val listener = firstArg<ProgressListener<String>>()
            listener.run("""<tool_call>{"name":"tavily_web_search","arguments":""", true)
            Futures.immediateFuture("")
        }

        val service = MediaPipeInferenceServiceImpl(
            llmInference = llmInference,
            context = context,
            modelType = ModelType.FAST,
            activeModelProvider = activeModelProvider,
            processThinkingTokens = ProcessThinkingTokensUseCase(),
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "What are the best folding phones?",
            options = GenerationOptions(
                reasoningBudget = 0,
                modelType = ModelType.FAST,
                systemPrompt = "Use tools when needed.\n<tool_call>{\"name\":\"tavily_web_search\",\"arguments\":{\"query\":\"...\"}}</tool_call>",
            ),
            closeConversation = false,
        ).toList()

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Malformed tool_call envelope", error.cause.message)
    }
}
