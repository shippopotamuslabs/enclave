package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LlmToolingOrchestrator].
 *
 * These tests verify the core tool-calling loop logic shared across all LLM inference
 * services: single tool call, multiple sequential tool calls, no-tool-call passthrough,
 * recursion limits, and unsupported-tool rejection.
 */
class LlmToolingOrchestratorTest {

    private lateinit var toolExecutor: ToolExecutorPort
    private lateinit var loggingPort: LoggingPort
    private lateinit var orchestrator: LlmToolingOrchestrator

    @BeforeEach
    fun setup() {
        toolExecutor = mockk()
        loggingPort = mockk<LoggingPort>(relaxed = true)
        orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort)
    }

    // ── No tool call scenarios ──────────────────────────────────────────────

    @Test
    fun `onNoToolCallOnFirstPass invoked when first pass has no tool call`() = runTest {
        var noToolCallCallbackInvoked = false
        var finishedParams: String? = null
        var finishedToolCallCount = -1

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ -> "response-no-tool" },
            onToolCallDetected = { emptyList() },
            onToolResultsMapped = { _, _, _ -> "no-change" },
            onNoToolCallOnFirstPass = { noToolCallCallbackInvoked = true },
            onFinished = { params, toolCallCount, _ ->
                finishedParams = params
                finishedToolCallCount = toolCallCount
            },
        )

        assertTrue(noToolCallCallbackInvoked)
        assertEquals("initial", finishedParams)
        assertEquals(0, finishedToolCallCount)
    }

    @Test
    fun `onFinished invoked with toolCallCount 0 when no tool call detected`() = runTest {
        var finishedToolCallCount = -1

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ -> "response-no-tool" },
            onToolCallDetected = { emptyList() },
            onToolResultsMapped = { params, _, _ -> params },
            onFinished = { _, toolCallCount, _ ->
                finishedToolCallCount = toolCallCount
            },
        )

        assertEquals(0, finishedToolCallCount)
    }

    // ── Single tool call scenarios ────────────────────────────────────────────

    @Test
    fun `single tool call executes tool and produces follow-up params`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val toolResult = ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"results":[]}""",
        )
        coEvery { toolExecutor.execute(any()) } returns toolResult

        var inferencePassCount = 0
        var finalParams = ""

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial-params",
            tag = "TestTag",
            onInferencePass = { params, allowToolCall ->
                inferencePassCount++
                when (inferencePassCount) {
                    1 -> "response-with-tool"
                    else -> "response-no-tool"
                }
            },
            onToolCallDetected = { response ->
                if (response == "response-with-tool") listOf(toolRequest) else emptyList()
            },
            onToolResultsMapped = { _, _, results -> "follow-up-params-${results.first().second}" },
            onFinished = { params, toolCallCount, _ ->
                finalParams = params
            },
        )

        assertEquals(2, inferencePassCount)
        coVerify(exactly = 1) { toolExecutor.execute(any()) }
        assertTrue(finalParams.startsWith("follow-up-params-"))
    }

    // ── Multiple sequential tool calls ─────────────────────────────────────────

    @Test
    fun `multiple sequential tool calls execute tools in order and continue loop`() = runTest {
        val toolRequest1 = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"first"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val toolRequest2 = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"second"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"results":[]}""",
        )

        var inferencePassCount = 0
        var finishedToolCallCount = -1

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { params, allowToolCall ->
                inferencePassCount++
                when (inferencePassCount) {
                    1 -> "response-tool-1"
                    2 -> "response-tool-2"
                    else -> "response-no-tool"
                }
            },
            onToolCallDetected = { response ->
                when (response) {
                    "response-tool-1" -> listOf(toolRequest1)
                    "response-tool-2" -> listOf(toolRequest2)
                    else -> emptyList()
                }
            },
            onToolResultsMapped = { _, _, results -> "follow-up-params" },
            onFinished = { _, toolCallCount, _ ->
                finishedToolCallCount = toolCallCount
            },
        )

        assertEquals(3, inferencePassCount)
        coVerify(exactly = 2) { toolExecutor.execute(any()) }
        assertEquals(2, finishedToolCallCount)
    }

    @Test
    fun `allowToolCall is true while under limit and false at limit`() = runTest {
        val maxToolCalls = 2
        val allowToolCallValues = mutableListOf<Boolean>()

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            maxToolCalls = maxToolCalls,
            onInferencePass = { _, allowToolCall ->
                allowToolCallValues.add(allowToolCall)
                "response-no-tool"
            },
            onToolCallDetected = { emptyList() },
            onToolResultsMapped = { params, _, _ -> params },
            onFinished = { _, _, _ -> },
        )

        // Only one pass since there's no tool call, allowToolCall should be true (0 < 2)
        assertEquals(1, allowToolCallValues.size)
        assertTrue(allowToolCallValues[0])
    }

    @Test
    fun `allowToolCall decrements correctly across multiple tool calls`() = runTest {
        val maxToolCalls = 2
        var toolCallIndex = 0
        val toolRequests = listOf(
            ToolCallRequest("tavily_web_search", """{"query":"q1"}""", "TEST", ModelType.FAST),
            ToolCallRequest("tavily_web_search", """{"query":"q2"}""", "TEST", ModelType.FAST),
        )
        val allowToolCallValues = mutableListOf<Boolean>()
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("tavily_web_search", "{}")

        var inferencePassCount = 0
        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            maxToolCalls = maxToolCalls,
            onInferencePass = { _, allowToolCall ->
                allowToolCallValues.add(allowToolCall)
                inferencePassCount++
                when (inferencePassCount) {
                    1 -> "tool-call-1"
                    2 -> "tool-call-2"
                    else -> "no-tool"
                }
            },
            onToolCallDetected = { response ->
                when (response) {
                    "tool-call-1" -> listOf(toolRequests[0])
                    "tool-call-2" -> listOf(toolRequests[1])
                    else -> emptyList()
                }
            },
            onToolResultsMapped = { _, _, _ -> "follow-up" },
            onFinished = { _, _, _ -> },
        )

        // Pass 1: toolCallCount=0, allowToolCall = 0 < 2 = true
        // Pass 2: toolCallCount=1, allowToolCall = 1 < 2 = true
        // Pass 3: toolCallCount=2, allowToolCall = 2 < 2 = false (but no tool call, loop exits)
        assertEquals(3, allowToolCallValues.size)
        assertTrue(allowToolCallValues[0])  // 0 < 2
        assertTrue(allowToolCallValues[1])  // 1 < 2
        assertFalse(allowToolCallValues[2]) // 2 < 2 = false
    }

    // ── Recursion limit ───────────────────────────────────────────────────────

    @Test
    fun `throws IllegalStateException when tool call count exceeds maxToolCalls`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = "{}",
        )

        var inferencePassCount = 0

        val exception = try {
            orchestrator.execute<String, String>(
                providerName = "TEST",
                initialParams = "initial",
                tag = "TestTag",
                maxToolCalls = 1,
                onInferencePass = { _, _ ->
                    inferencePassCount++
                    "response-with-tool"
                },
                onToolCallDetected = { listOf(toolRequest) },
                onToolResultsMapped = { _, _, _ -> "follow-up" },
                onFinished = { _, _, _ -> },
            )
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertEquals("Search skill recursion limit exceeded", exception?.message)
    }

    @Test
    fun `does not execute tool when maxToolCalls is zero`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )

        val exception = try {
            orchestrator.execute<String, String>(
                providerName = "TEST",
                initialParams = "initial",
                tag = "TestTag",
                maxToolCalls = 0,
                onInferencePass = { _, _ -> "response-with-tool" },
                onToolCallDetected = { listOf(toolRequest) },
                onToolResultsMapped = { _, _, _ -> "follow-up" },
                onFinished = { _, _, _ -> },
            )
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertEquals("Search skill recursion limit exceeded", exception?.message)
    }

    // ── Unsupported tool rejection ──────────────────────────────────────────────

    @Test
    fun `throws IllegalArgumentException for unsupported tool name`() = runTest {
        val badToolRequest = ToolCallRequest(
            toolName = "unknown_tool",
            argumentsJson = """{"q":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )

        val exception = try {
            orchestrator.execute<String, String>(
                providerName = "TEST",
                initialParams = "initial",
                tag = "TestTag",
                onInferencePass = { _, _ -> "response-with-bad-tool" },
                onToolCallDetected = { listOf(badToolRequest) },
                onToolResultsMapped = { _, _, _ -> "follow-up" },
                onFinished = { _, _, _ -> },
            )
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertTrue(exception?.message?.contains("Unsupported tool") == true)
    }

    // ── Tool executor receives correct ToolCallRequest ──────────────────────────

    @Test
    fun `toolExecutor receives the exact ToolCallRequest from onToolCallDetected`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"android news"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
            chatId = com.browntowndev.pocketcrew.domain.model.chat.ChatId("chat-123"),
            userMessageId = com.browntowndev.pocketcrew.domain.model.chat.MessageId("msg-456"),
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"results":[]}""",
        )

        var inferencePassCount = 0
        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) "tool" else "no-tool"
            },
            onToolCallDetected = { if (it == "tool") listOf(toolRequest) else emptyList() },
            onToolResultsMapped = { _, _, _ -> "follow-up" },
            onFinished = { _, _, _ -> },
        )

        val capturedRequest = slot<ToolCallRequest>()
        coVerify { toolExecutor.execute(capture(capturedRequest)) }
        assertEquals("tavily_web_search", capturedRequest.captured.toolName)
        assertEquals("""{"query":"android news"}""", capturedRequest.captured.argumentsJson)
    }

    // ── onToolResultsMapped receives correct arguments ───────────────────────────

    @Test
    fun `onToolResultsMapped receives current params, response, and tool results`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val toolResult = ToolExecutionResult("tavily_web_search", """{"results":[{"url":"https://example.com"}]}""")
        coEvery { toolExecutor.execute(any()) } returns toolResult

        var mappedParams: String? = null
        var mappedResponse: String? = null
        var mappedResultJson: String? = null

        var inferencePassCount = 0
        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial-params",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) "tool-response" else "no-tool"
            },
            onToolCallDetected = { if (it == "tool-response") listOf(toolRequest) else emptyList() },
            onToolResultsMapped = { params, response, results ->
                mappedParams = params
                mappedResponse = response
                mappedResultJson = results.first().second
                "updated-params"
            },
            onFinished = { _, _, _ -> },
        )

        // onToolResultsMapped should receive the *current* params (before this loop iteration),
        // the current response, and the tool results
        assertEquals("initial-params", mappedParams)
        assertEquals("tool-response", mappedResponse)
        assertEquals("""{"results":[{"url":"https://example.com"}]}""", mappedResultJson)
    }

    // ── Default maxToolCalls allows many iterations ──────────────────────────────

    @Test
    fun `default maxToolCalls allows sequential tool calls below limit`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = "{}",
        )

        // Simulate 2 tool calls (under the default limit of 3) then a final text response
        var inferencePassCount = 0
        var finishedToolCallCount = -1

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount <= 2) "tool-call" else "no-tool"
            },
            onToolCallDetected = { response ->
                if (response == "tool-call") listOf(toolRequest) else emptyList()
            },
            onToolResultsMapped = { _, _, _ -> "follow-up" },
            onFinished = { _, toolCallCount, _ ->
                finishedToolCallCount = toolCallCount
            },
        )

        assertEquals(3, inferencePassCount)
        assertEquals(2, finishedToolCallCount)
        coVerify(exactly = 2) { toolExecutor.execute(any()) }
    }

    // ── Logging verification ───────────────────────────────────────────────────

    @Test
    fun `logs tool call detection and completion`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"results":[]}""",
        )

        var inferencePassCount = 0
        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) "tool-call" else "no-tool"
            },
            onToolCallDetected = { if (it == "tool-call") listOf(toolRequest) else emptyList() },
            onToolResultsMapped = { _, _, _ -> "follow-up" },
            onFinished = { _, _, _ -> },
        )

        io.mockk.verify {
            loggingPort.info(
                "TestTag",
                match { it.contains("Tool call detected") && it.contains("tavily_web_search") && it.contains("iteration=1") }
            )
        }
        io.mockk.verify {
            loggingPort.info(
                "TestTag",
                match { it.contains("Tool call completed") && it.contains("tavily_web_search") }
            )
        }
    }

    @Test
    fun `logs no tool call on first pass when no tool call detected`() = runTest {
        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ -> "no-tool" },
            onToolCallDetected = { emptyList() },
            onToolResultsMapped = { params, _, _ -> params },
            onFinished = { _, _, _ -> },
        )

        io.mockk.verify {
            loggingPort.info(
                "TestTag",
                match { it.contains("Tool loop complete without tool call") && it.contains("TEST") }
            )
        }
    }

    // ── onFinished receives last response ────────────────────────────────────────

    @Test
    fun `onFinished receives last response from final inference pass`() = runTest {
        var lastResponseReceived: String? = null

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ -> "final-response" },
            onToolCallDetected = { emptyList() },
            onToolResultsMapped = { params, _, _ -> params },
            onFinished = { _, _, lastResponse ->
                lastResponseReceived = lastResponse
            },
        )

        assertEquals("final-response", lastResponseReceived)
    }

    @Test
    fun `onFinished receives last response after tool call loop`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("tavily_web_search", "{}")

        var inferencePassCount = 0
        var lastResponseReceived: String? = null

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) "tool-response" else "final-text-response"
            },
            onToolCallDetected = { if (it == "tool-response") listOf(toolRequest) else emptyList() },
            onToolResultsMapped = { _, _, _ -> "follow-up" },
            onFinished = { _, _, lastResponse ->
                lastResponseReceived = lastResponse
            },
        )

        assertEquals("final-text-response", lastResponseReceived)
    }

    // ── Params chaining across tool calls ────────────────────────────────────────

    @Test
    fun `initial params passed to first inference pass and updated params passed to subsequent passes`() = runTest {
        val toolRequest = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"test"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("tavily_web_search", """{"result":"data"}""")

        val receivedParams = mutableListOf<String>()
        var inferencePassCount = 0

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial-params",
            tag = "TestTag",
            onInferencePass = { params, _ ->
                receivedParams.add(params)
                inferencePassCount++
                if (inferencePassCount == 1) "tool-call" else "no-tool"
            },
            onToolCallDetected = { if (it == "tool-call") listOf(toolRequest) else emptyList() },
            onToolResultsMapped = { _, _, results -> "updated-${results.first().second}" },
            onFinished = { _, _, _ -> },
        )

        assertEquals(listOf("initial-params", "updated-{\"result\":\"data\"}"), receivedParams)
    }

    // ── Parallel tool calls (multiple in single response) ───────────────────────

    @Test
    fun `parallel tool calls in a single response are all executed sequentially`() = runTest {
        val toolRequest1 = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"first"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val toolRequest2 = ToolCallRequest(
            toolName = "attached_image_inspect",
            argumentsJson = """{"question":"describe this"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val result1 = ToolExecutionResult("tavily_web_search", """{"results":[]}""")
        val result2 = ToolExecutionResult("attached_image_inspect", """{"answer":"an image"}""")
        coEvery { toolExecutor.execute(any()) } returnsMany listOf(result1, result2)

        var inferencePassCount = 0
        var finishedToolCallCount = -1

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) "parallel-tool-response" else "no-tool"
            },
            onToolCallDetected = { response ->
                if (response == "parallel-tool-response") listOf(toolRequest1, toolRequest2) else emptyList()
            },
            onToolResultsMapped = { _, _, results ->
                assertEquals(2, results.size)
                "follow-up"
            },
            onFinished = { _, toolCallCount, _ ->
                finishedToolCallCount = toolCallCount
            },
        )

        // Only 2 inference passes: 1 for the parallel response, 1 for the follow-up with no tools
        assertEquals(2, inferencePassCount)
        // Both tool calls were executed
        coVerify(exactly = 2) { toolExecutor.execute(any()) }
        // Tool call count reflects both parallel calls
        assertEquals(2, finishedToolCallCount)
    }

    @Test
    fun `onToolResultsMapped receives all parallel tool call results at once`() = runTest {
        val toolRequest1 = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"first"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val toolRequest2 = ToolCallRequest(
            toolName = "tavily_web_search",
            argumentsJson = """{"query":"second"}""",
            provider = "TEST",
            modelType = ModelType.FAST,
        )
        val result1 = ToolExecutionResult("tavily_web_search", """{"r1":"data1"}""")
        val result2 = ToolExecutionResult("tavily_web_search", """{"r2":"data2"}""")
        coEvery { toolExecutor.execute(any()) } returnsMany listOf(result1, result2)

        val mappedParamsList = mutableListOf<String>()
        var inferencePassCount = 0

        orchestrator.execute<String, String>(
            providerName = "TEST",
            initialParams = "initial",
            tag = "TestTag",
            onInferencePass = { _, _ ->
                inferencePassCount++
                if (inferencePassCount == 1) "parallel" else "no-tool"
            },
            onToolCallDetected = { if (it == "parallel") listOf(toolRequest1, toolRequest2) else emptyList() },
            onToolResultsMapped = { params, _, results ->
                val mapped = "$params+${results[0].second}+${results[1].second}"
                mappedParamsList.add(mapped)
                mapped
            },
            onFinished = { _, _, _ -> },
        )

        // Only one call to onToolResultsMapped for the turn
        assertEquals(1, mappedParamsList.size)
        assertEquals("initial+{\"r1\":\"data1\"}+{\"r2\":\"data2\"}", mappedParamsList[0])
    }

    @Test
    fun `parallel tool calls increment toolCallCount and hit recursion limit correctly`() = runTest {
        val toolRequest1 = ToolCallRequest("tavily_web_search", """{"q":"1"}""", "TEST", ModelType.FAST)
        val toolRequest2 = ToolCallRequest("tavily_web_search", """{"q":"2"}""", "TEST", ModelType.FAST)
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("tavily_web_search", "{}")

        val exception = try {
            orchestrator.execute<String, String>(
                providerName = "TEST",
                initialParams = "initial",
                tag = "TestTag",
                maxToolCalls = 1,
                onInferencePass = { _, _ -> "parallel" },
                onToolCallDetected = { listOf(toolRequest1, toolRequest2) },
                onToolResultsMapped = { _, _, _ -> "follow-up" },
                onFinished = { _, _, _ -> },
            )
            null
        } catch (e: IllegalStateException) {
            e
        }

        // The first parallel call succeeds (count=1), the second hits the limit (1 >= 1)
        assertEquals("Search skill recursion limit exceeded", exception?.message)
    }
}

private fun assertFalse(value: Boolean) {
    org.junit.jupiter.api.Assertions.assertFalse(value)
}