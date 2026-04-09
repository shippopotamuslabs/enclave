package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.openai.client.OpenAIClient
import com.openai.core.http.StreamResponse
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCompletedEvent
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseError
import com.openai.models.responses.ResponseFailedEvent
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent
import com.openai.models.responses.ResponseOutputItem
import com.openai.models.responses.ResponseOutputItemAddedEvent
import com.openai.models.responses.ResponseReasoningTextDeltaEvent
import com.openai.models.responses.ResponseStreamEvent
import com.openai.models.responses.ResponseTextDeltaEvent
import com.openai.services.blocking.ResponseService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import java.util.stream.Stream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterInferenceServiceImplTest {

    @Test
    fun `search enabled prompt streams full tool round trip and keeps OpenRouter routing body`() = runTest {
        val client = mockk<OpenAIClient>()
        val responseService = mockk<ResponseService>()
        val initialStream = mockk<StreamResponse<ResponseStreamEvent>>()
        val followUpStream = mockk<StreamResponse<ResponseStreamEvent>>()
        val toolExecutor = mockk<ToolExecutorPort>()
        val capturedParams = mutableListOf<ResponseCreateParams>()

        every { client.responses() } returns responseService
        every { responseService.createStreaming(capture(capturedParams)) } returnsMany listOf(
            initialStream,
            followUpStream,
        )
        every { initialStream.stream() } returns Stream.of(
            mockReasoningEvent("Need to search first."),
            mockOutputTextEvent("Let me search for that now."),
            mockFunctionCallOutputItemAddedEvent(),
            mockFunctionCallDoneEvent(),
            mockCompletedEvent("resp_call_1"),
        )
        every { initialStream.close() } returns Unit
        every { followUpStream.stream() } returns Stream.of(
            mockReasoningEvent("Reviewing search result."),
            mockOutputTextEvent("Use the search result summary."),
            mockCompletedEvent("resp_followup"),
        )
        every { followUpStream.close() } returns Unit

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = OpenRouterInferenceServiceImpl(
            client = client,
            modelId = "openai/gpt-5.2:nitro",
            modelType = ModelType.FAST,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "Find recent Android agent news",
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.LOW,
                maxTokens = 128,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to search first." })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Reviewing search result." })
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Use the search result summary." })
        assertEquals(1, events.filterIsInstance<InferenceEvent.Finished>().size)
        coVerify(exactly = 1) { toolExecutor.execute(any()) }

        val firstRequest = capturedParams.first()
        val secondRequest = capturedParams[1]
        assertTrue(firstRequest._additionalBodyProperties().containsKey("provider"))
        assertTrue(firstRequest._additionalBodyProperties()["provider"].toString().contains("allow_fallbacks"))
        assertTrue(firstRequest.toString().contains("tavily_web_search"))
        assertTrue(secondRequest._additionalBodyProperties().containsKey("provider"))
        assertTrue(secondRequest.input().get().toString().contains("Let me search for that now."))
        assertTrue(secondRequest.input().get().toString().contains("fc_call_1"))
        assertTrue(secondRequest.reasoning().isPresent)
        assertEquals(128L, secondRequest.maxOutputTokens().get())
    }

    @Test
    fun `search follow-up stream tolerates recoverable internal stream termination after partial output`() = runTest {
        val client = mockk<OpenAIClient>()
        val responseService = mockk<ResponseService>()
        val initialStream = mockk<StreamResponse<ResponseStreamEvent>>()
        val followUpStream = mockk<StreamResponse<ResponseStreamEvent>>()
        val toolExecutor = mockk<ToolExecutorPort>()

        every { client.responses() } returns responseService
        every { responseService.createStreaming(any<ResponseCreateParams>()) } returnsMany listOf(
            initialStream,
            followUpStream,
        )
        every { initialStream.stream() } returns Stream.of(
            mockReasoningEvent("Need to search first."),
            mockFunctionCallOutputItemAddedEvent(),
            mockFunctionCallDoneEvent(),
            mockCompletedEvent("resp_call_1"),
        )
        every { initialStream.close() } returns Unit
        every { followUpStream.stream() } returns Stream.of(
            mockReasoningEvent("Reviewing search result."),
            mockOutputTextEvent("Use the search result summary."),
            mockFailedEvent("internal stream ended unexpectedly"),
        )
        every { followUpStream.close() } returns Unit

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = OpenRouterInferenceServiceImpl(
            client = client,
            modelId = "openai/gpt-5.2:nitro",
            modelType = ModelType.FAST,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "Find recent Android agent news",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Reviewing search result." })
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Use the search result summary." })
        assertEquals(1, events.filterIsInstance<InferenceEvent.Finished>().size)
        assertTrue(events.none { it is InferenceEvent.Error })
        coVerify(exactly = 1) { toolExecutor.execute(any()) }
    }

    private fun mockOutputTextEvent(text: String): ResponseStreamEvent {
        val delta = mockk<ResponseTextDeltaEvent>()
        every { delta.delta() } returns text
        every { delta.itemId() } returns "item_1"
        every { delta.outputIndex() } returns 0L
        every { delta.contentIndex() } returns 0L

        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isOutputTextDelta() } returns true
        every { event.outputTextDelta() } returns Optional.of(delta)
        return event
    }

    private fun mockReasoningEvent(text: String): ResponseStreamEvent {
        val delta = mockk<ResponseReasoningTextDeltaEvent>()
        every { delta.delta() } returns text
        every { delta.itemId() } returns "reasoning_1"
        every { delta.outputIndex() } returns 0L
        every { delta.contentIndex() } returns 0L

        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isReasoningTextDelta() } returns true
        every { event.reasoningTextDelta() } returns Optional.of(delta)
        return event
    }

    private fun mockCompletedEvent(responseId: String): ResponseStreamEvent {
        val completed = mockk<ResponseCompletedEvent>()
        val response = mockk<Response>()
        every { response.id() } returns responseId
        every { completed.response() } returns response

        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isCompleted() } returns true
        every { event.completed() } returns Optional.of(completed)
        return event
    }

    private fun mockFunctionCallDoneEvent(): ResponseStreamEvent {
        val functionCallDone = mockk<ResponseFunctionCallArgumentsDoneEvent>()
        every { functionCallDone.name() } returns "tavily_web_search"
        every { functionCallDone.arguments() } returns """{"query":"latest android tool calling"}"""
        every { functionCallDone.itemId() } returns "fc_item_1"

        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isFunctionCallArgumentsDone() } returns true
        every { event.functionCallArgumentsDone() } returns Optional.of(functionCallDone)
        return event
    }

    private fun mockFunctionCallOutputItemAddedEvent(): ResponseStreamEvent {
        val functionCall = mockk<ResponseFunctionToolCall>()
        every { functionCall.id() } returns Optional.of("fc_item_1")
        every { functionCall.callId() } returns "fc_call_1"
        every { functionCall.name() } returns "tavily_web_search"
        every { functionCall.arguments() } returns """{"query":"latest android tool calling"}"""

        val item = mockk<ResponseOutputItem>()
        every { item.isFunctionCall() } returns true
        every { item.asFunctionCall() } returns functionCall

        val outputItemAdded = mockk<ResponseOutputItemAddedEvent>()
        every { outputItemAdded.item() } returns item

        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isOutputItemAdded() } returns true
        every { event.outputItemAdded() } returns Optional.of(outputItemAdded)
        return event
    }

    private fun mockFailedEvent(message: String): ResponseStreamEvent {
        val error = mockk<ResponseError>()
        every { error.message() } returns message
        val response = mockk<Response>()
        every { response.error() } returns Optional.of(error)
        val failed = mockk<ResponseFailedEvent>()
        every { failed.response() } returns response

        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isFailed() } returns true
        every { event.failed() } returns Optional.of(failed)
        return event
    }
}
