package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.openai.client.OpenAIClient
import com.openai.core.http.StreamResponse
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseOutputItem
import com.openai.models.responses.ResponseOutputMessage
import com.openai.models.responses.ResponseOutputText
import com.openai.models.responses.ResponseStreamEvent
import com.openai.models.responses.ResponseTextDeltaEvent
import com.openai.models.responses.ResponseCompletedEvent
import com.openai.models.responses.ResponseReasoningTextDeltaEvent
import com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent
import com.openai.services.blocking.ResponseService
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import java.util.Optional
import java.util.stream.Stream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiInferenceServiceImplTest {

    @Test
    fun `setHistory clears and adds messages`() = runBlocking {
        val client = mockk<OpenAIClient>()
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = mockk<LoggingPort>(relaxed = true)
        )

        val messages = listOf(ChatMessage(Role.USER, "Hello"))
        service.setHistory(messages)
        
        // Since conversationHistory is private, we're just testing it doesn't crash
        // A full test would mock the stream response and verify the params passed to createStreaming.

        service.closeSession()
    }

    @Test
    fun `mergeSystemPrompt prepends configured prompt when history lacks system message`() {
        val service = ApiInferenceServiceImpl(
            client = mockk(),
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = mockk<LoggingPort>(relaxed = true)
        )

        val merged = service.mergeSystemPrompt(
            history = listOf(ChatMessage(Role.USER, "hello")),
            systemPrompt = "You are Grok."
        )

        assertEquals(
            listOf(
                ChatMessage(Role.SYSTEM, "You are Grok."),
                ChatMessage(Role.USER, "hello")
            ),
            merged
        )
    }

    @Test
    fun `mergeSystemPrompt ignores blank prompt`() {
        val service = ApiInferenceServiceImpl(
            client = mockk(),
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = mockk<LoggingPort>(relaxed = true)
        )

        val history = listOf(ChatMessage(Role.USER, "hello"))
        val merged = service.mergeSystemPrompt(history, "   ")

        assertEquals(history, merged)
    }

    @Test
    fun `search enabled prompt completes one tool round trip before final assistant text`() = runTest {
        val client = mockk<OpenAIClient>()
        val responseService = mockk<ResponseService>()
        val initialStreamResponse = mockk<StreamResponse<ResponseStreamEvent>>()
        val followUpStreamResponse = mockk<StreamResponse<ResponseStreamEvent>>()
        val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
        every { client.responses() } returns responseService
        every { responseService.createStreaming(any<ResponseCreateParams>()) } returnsMany listOf(
            initialStreamResponse,
            followUpStreamResponse,
        )
        every { initialStreamResponse.stream() } returns Stream.of(
            mockReasoningEvent("Need to search first."),
            mockFunctionCallDoneEvent(),
            mockCompletedEvent(responseId = "resp_call_1"),
        )
        every { initialStreamResponse.close() } returns Unit
        every { followUpStreamResponse.stream() } returns Stream.of(
            mockReasoningEvent("Need to review search results."),
            mockOutputTextEvent("Use the search result summary."),
            mockCompletedEvent(responseId = "resp_followup"),
        )
        every { followUpStreamResponse.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
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

        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to search first." })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to review search results." })
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Use the search result summary." })
        assertTrue(events.any { it is InferenceEvent.Finished && it.modelType == ModelType.FAST })
        coVerify(exactly = 1) { toolExecutor.execute(any()) }
    }

    @Test
    fun `search enabled prompt rejects recursive second tool request`() = runTest {
        val client = mockk<OpenAIClient>()
        val responseService = mockk<ResponseService>()
        val initialStreamResponse = mockk<StreamResponse<ResponseStreamEvent>>()
        val followUpStreamResponse = mockk<StreamResponse<ResponseStreamEvent>>()
        val toolExecutor = mockk<ToolExecutorPort>()
        every { client.responses() } returns responseService
        every { responseService.createStreaming(any<ResponseCreateParams>()) } returnsMany listOf(
            initialStreamResponse,
            followUpStreamResponse,
        )
        every { initialStreamResponse.stream() } returns Stream.of(
            mockFunctionCallDoneEvent(),
            mockCompletedEvent(responseId = "resp_call_1"),
        )
        every { initialStreamResponse.close() } returns Unit
        every { followUpStreamResponse.stream() } returns Stream.of(mockFunctionCallDoneEvent())
        every { followUpStreamResponse.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "Search twice",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Search skill recursion limit exceeded", error.cause.message)
    }

    private fun mockTextResponse(text: String): Response {
        val outputText = mockk<ResponseOutputText>()
        every { outputText.text() } returns text

        val content = mockk<ResponseOutputMessage.Content>()
        every { content.isOutputText() } returns true
        every { content.asOutputText() } returns outputText

        val message = mockk<ResponseOutputMessage>()
        every { message.content() } returns listOf(content)

        val outputItem = mockk<ResponseOutputItem>()
        every { outputItem.isFunctionCall() } returns false
        every { outputItem.isMessage() } returns true
        every { outputItem.asMessage() } returns message

        val response = mockk<Response>()
        every { response.output() } returns listOf(outputItem)
        return response
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

    private fun mockCompletedEvent(responseId: String = "resp_1"): ResponseStreamEvent {
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
        every { functionCallDone.itemId() } returns "call_1"
        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isFunctionCallArgumentsDone() } returns true
        every { event.functionCallArgumentsDone() } returns Optional.of(functionCallDone)
        return event
    }
}
