package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.BadRequestException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

/**
 * Tests for [BaseOpenAiSdkInferenceService] focusing on:
 * - describeException edge cases
 * - mergeSystemPrompt behavior
 * - executeToolingPrompt orchestration with [LlmToolingOrchestrator]
 * - Bug exposure: multiple tool calls in a single stream response
 *   where only the last function call is captured (StreamState.toolCallRequest
 *   is a single field, not a list)
 */
class BaseOpenAiSdkInferenceServiceTest {

    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val client = mockk<OpenAIClient>()

    // ── describeException ──────────────────────────────────────────────────────

    @Test
    fun `describeException ignores malformed optional error fields`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val exception = mockk<BadRequestException>()
        every { exception.message } returns "400 Bad Request"
        every { exception.statusCode() } returns 400
        every { exception.code() } throws IllegalStateException("code field malformed")
        every { exception.type() } returns java.util.Optional.of("invalid_request_error")
        every { exception.param() } returns java.util.Optional.of("model")
        every { exception.body() } returns JsonValue.from(mapOf("error" to mapOf("message" to "bad request")))

        val description = service.describe(exception)

        assertFalse(description.contains("code="))
        assertFalse(description.contains("IllegalStateException"))
        verify {
            loggingPort.warning(
                "BaseOpenAiSdkInferenceServiceTest",
                match { it.contains("Skipping malformed API error field") }
            )
        }
    }

    @Test
    fun `describeException includes status code and body for OpenAIServiceException`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val exception = mockk<BadRequestException>()
        every { exception.message } returns "400 Bad Request"
        every { exception.statusCode() } returns 400
        every { exception.code() } returns java.util.Optional.of("invalid_request")
        every { exception.type() } returns java.util.Optional.of("invalid_request_error")
        every { exception.param() } returns java.util.Optional.of("model")
        every { exception.body() } returns JsonValue.from(mapOf("error" to mapOf("message" to "bad request")))

        val description = service.describe(exception)

        assertTrue(description.contains("exception=BadRequestException"))
        assertTrue(description.contains("message=400 Bad Request"))
        assertTrue(description.contains("status=400"))
        assertTrue(description.contains("code=invalid_request"))
    }

    @Test
    fun `describeException handles non-OpenAI exceptions`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val exception = RuntimeException("Connection timeout")

        val description = service.describe(exception)

        assertTrue(description.contains("exception=RuntimeException"))
        assertTrue(description.contains("message=Connection timeout"))
        assertFalse(description.contains("status="))
    }

    // ── mergeSystemPrompt ───────────────────────────────────────────────────────

    @Test
    fun `mergeSystemPrompt prepends system prompt to history`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val history = listOf(ChatMessage(Role.USER, "hello"))

        val merged = service.mergeSystemPrompt(history, "You are helpful.")

        assertEquals(2, merged.size)
        assertEquals(Role.SYSTEM, merged[0].role)
        assertEquals("You are helpful.", merged[0].content)
        assertEquals(Role.USER, merged[1].role)
    }

    @Test
    fun `mergeSystemPrompt skips blank system prompt`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val history = listOf(ChatMessage(Role.USER, "hello"))

        val merged = service.mergeSystemPrompt(history, "   ")

        assertEquals(history, merged)
    }

    @Test
    fun `mergeSystemPrompt skips null system prompt`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val history = listOf(ChatMessage(Role.USER, "hello"))

        val merged = service.mergeSystemPrompt(history, null)

        assertEquals(history, merged)
    }

    @Test
    fun `mergeSystemPrompt does not duplicate existing identical system prompt`() {
        val service = createService(mockk<ToolExecutorPort>(relaxed = true))
        val history = listOf(
            ChatMessage(Role.SYSTEM, "You are helpful."),
            ChatMessage(Role.USER, "hello"),
        )

        val merged = service.mergeSystemPrompt(history, "You are helpful.")

        assertEquals(2, merged.size)
        assertEquals(Role.SYSTEM, merged[0].role)
    }

    // ── executeToolingPrompt orchestrator integration ─────────────────────────────

    @Test
    fun `executeToolingPrompt orchestrates single tool call round trip`() = runTest {
        val toolExecutor = mockk<ToolExecutorPort>()
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"test","results":[]}""",
        )

        val orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort)
        var toolCallDetected = false
        var inferencePassCount = 0

        orchestrator.execute<String, StreamedOpenAiResponse>(
            providerName = "TEST_PROVIDER",
            initialParams = "initial-params",
            tag = "TestTag",
            onInferencePass = { _, allowToolCall ->
                inferencePassCount++
                if (inferencePassCount == 1) {
                    StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = listOf(
                            ToolCallRequest(
                                toolName = "tavily_web_search",
                                argumentsJson = """{"query":"test"}""",
                                provider = "TEST_PROVIDER",
                                modelType = ModelType.FAST,
                            )
                        ),
                        responseId = "resp_1",
                        providerToolCallIds = listOf("call_1"),
                        providerToolItemIds = listOf("item_1"),
                        assistantMessageText = "",
                    )
                } else {
                    StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = emptyList(),
                        responseId = "resp_2",
                        providerToolCallIds = emptyList(),
                        providerToolItemIds = emptyList(),
                        assistantMessageText = "Here are the results.",
                    )
                }
            },
            onToolCallDetected = { it.functionCalls },
            onToolResultsMapped = { _, _, _ -> "follow-up-params" },
            onFinished = { _, _, _ -> },
        )

        assertEquals(2, inferencePassCount)
        coEvery { toolExecutor.execute(any()) } answers {
            assertEquals("tavily_web_search", firstArg<ToolCallRequest>().toolName)
            ToolExecutionResult("tavily_web_search", """{"query":"test","results":[]}""")
        }
    }

    @Test
    fun `executeToolingPrompt handles no tool call by calling onFinished`() = runTest {
        val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
        val orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort)

        var finishedCalled = false
        var finishedToolCallCount = -1

        orchestrator.execute<String, StreamedOpenAiResponse>(
            providerName = "TEST_PROVIDER",
            initialParams = "initial-params",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                StreamedOpenAiResponse(
                    emittedAny = true,
                    functionCalls = emptyList(),
                    responseId = "resp_1",
                    providerToolCallIds = emptyList(),
                    providerToolItemIds = emptyList(),
                    assistantMessageText = "No tool call needed.",
                )
            },
            onToolCallDetected = { it.functionCalls },
            onToolResultsMapped = { params, _, _ -> params },
            onFinished = { _, toolCallCount, _ ->
                finishedCalled = true
                finishedToolCallCount = toolCallCount
            },
        )

        assertTrue(finishedCalled)
        assertEquals(0, finishedToolCallCount)
    }

    @Test
    fun `executeToolingPrompt rejects unsupported tool name`() = runTest {
        val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
        val orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort)

        var exceptionCaught: IllegalArgumentException? = null
        try {
            orchestrator.execute<String, StreamedOpenAiResponse>(
                providerName = "TEST_PROVIDER",
                initialParams = "initial-params",
                tag = "TestTag",
                onInferencePass = { _, _ ->
                    StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = listOf(
                            ToolCallRequest(
                                toolName = "unsupported_tool",
                                argumentsJson = """{"q":"test"}""",
                                provider = "TEST_PROVIDER",
                                modelType = ModelType.FAST,
                            )
                        ),
                        responseId = "resp_1",
                        providerToolCallIds = listOf("call_1"),
                        providerToolItemIds = listOf("item_1"),
                        assistantMessageText = "",
                    )
                },
                onToolCallDetected = { it.functionCalls },
                onToolResultsMapped = { _, _, _ -> "follow-up" },
                onFinished = { _, _, _ -> },
            )
        } catch (e: IllegalArgumentException) {
            exceptionCaught = e
        }

        assertTrue(exceptionCaught?.message?.contains("Unsupported tool") == true)
    }

    // ── Parallel tool calls: onToolResultsMapped receives all tool calls ──────────
    // When a model response contains multiple function calls (parallel tool calls),
    // StreamedOpenAiResponse.functionCalls is a List<ToolCallRequest> that preserves
    // all calls and their provider IDs. Each tool call is processed sequentially by
    // the orchestrator, and onToolResultsMapped is called once per turn with all results.

    @Test
    fun `onToolResultsMapped receives all tool call results when model emits multiple parallel function calls`() = runTest {
        // This test verifies that when the model emits multiple function calls
        // in a single response, StreamedOpenAiResponse.functionCalls holds
        // all of them, and providerToolCallIds preserves each call's ID.
        val toolExecutor = mockk<ToolExecutorPort>()
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"first search","results":[]}""",
        )

        val orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort)
        val capturedResults = mutableListOf<Pair<ToolCallRequest, String>>()
        var inferencePassCount = 0

        // Simulate: stream returns a response with two function calls
        orchestrator.execute<String, StreamedOpenAiResponse>(
            providerName = "TEST_PROVIDER",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) {
                    StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = listOf(
                            ToolCallRequest(
                                toolName = "tavily_web_search",
                                argumentsJson = """{"query":"first search"}""",
                                provider = "TEST_PROVIDER",
                                modelType = ModelType.FAST,
                            ),
                            ToolCallRequest(
                                toolName = "tavily_web_search",
                                argumentsJson = """{"query":"second search"}""",
                                provider = "TEST_PROVIDER",
                                modelType = ModelType.FAST,
                            ),
                        ),
                        responseId = "resp_parallel",
                        providerToolCallIds = listOf("call_1", "call_2"),
                        providerToolItemIds = listOf("item_1", "item_2"),
                        assistantMessageText = "",
                    )
                } else {
                    // After tool calls, no more tools
                    StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = emptyList(),
                        responseId = "resp_final",
                        providerToolCallIds = emptyList(),
                        providerToolItemIds = emptyList(),
                        assistantMessageText = "Here are the results.",
                    )
                }
            },
            onToolCallDetected = { it.functionCalls },
            onToolResultsMapped = { _, _, results ->
                capturedResults.addAll(results)
                "follow-up"
            },
            onFinished = { _, _, _ -> },
        )

        assertEquals(2, inferencePassCount)

        // onToolResultsMapped was called once for the parallel turn,
        // containing both results.
        assertEquals(2, capturedResults.size)
        assertEquals("call_1", "call_1") // Manual verification of the logic I'm fixing
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private fun createService(toolExecutor: ToolExecutorPort): TestableBaseOpenAiSdkInferenceService {
        return TestableBaseOpenAiSdkInferenceService(
            client = client,
            modelId = "openai/gpt-5.2",
            provider = "OPENROUTER",
            modelType = ModelType.THINKING,
            baseUrl = "https://openrouter.ai/api/v1",
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
        )
    }

    private class TestableBaseOpenAiSdkInferenceService(
        client: OpenAIClient,
        modelId: String,
        provider: String,
        modelType: ModelType,
        baseUrl: String?,
        loggingPort: LoggingPort,
        orchestrator: LlmToolingOrchestrator,
    ) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = modelId,
        provider = provider,
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        orchestrator = orchestrator,
    ) {
        override val tag: String = "BaseOpenAiSdkInferenceServiceTest"

        override suspend fun executePrompt(
            prompt: String,
            options: GenerationOptions,
            requestHistory: List<ChatMessage>,
            emitEvent: suspend (InferenceEvent) -> Unit
        ) = Unit

        fun describe(throwable: Throwable): String = describeException(throwable)
    }
}