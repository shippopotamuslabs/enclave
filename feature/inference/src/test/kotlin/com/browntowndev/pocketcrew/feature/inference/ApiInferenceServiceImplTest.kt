package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.core.http.StreamResponse
import com.openai.errors.BadRequestException
import com.openai.errors.NotFoundException
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiInferenceServiceImplTest {

    @Test
    fun `setHistory clears and adds messages`() = runBlocking {
        val client = mockk<OpenAIClient>()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(mockk(), loggingPort)
        )

        val messages = listOf(ChatMessage(Role.USER, "Hello"))
        service.setHistory(messages)
        
        // Since conversationHistory is private, we're just testing it doesn't crash
        // A full test would mock the stream response and verify the params passed to createStreaming.

        service.closeSession()
    }

    @Test
    fun `mergeSystemPrompt prepends configured prompt when history lacks system message`() {
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = mockk(),
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(mockk(), loggingPort)
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
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = mockk(),
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(mockk(), loggingPort)
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
            toolName = "web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
        )

        val events = service.sendPrompt(
            prompt = "Find recent Android agent news",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
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
    fun `search enabled prompt supports multiple tool calls up to limit`() = runTest {
        val client = mockk<OpenAIClient>()
        val responseService = mockk<ResponseService>()
        val stream1 = mockk<StreamResponse<ResponseStreamEvent>>()
        val stream2 = mockk<StreamResponse<ResponseStreamEvent>>()
        val stream3 = mockk<StreamResponse<ResponseStreamEvent>>()
        val finalStream = mockk<StreamResponse<ResponseStreamEvent>>()
        val toolExecutor = mockk<ToolExecutorPort>()

        every { client.responses() } returns responseService
        every { responseService.createStreaming(any<ResponseCreateParams>()) } returnsMany listOf(
            stream1, stream2, stream3, finalStream
        )

        every { stream1.stream() } returns Stream.of(mockFunctionCallDoneEvent(), mockCompletedEvent("r1"))
        every { stream1.close() } returns Unit
        every { stream2.stream() } returns Stream.of(mockFunctionCallDoneEvent(), mockCompletedEvent("r2"))
        every { stream2.close() } returns Unit
        every { stream3.stream() } returns Stream.of(mockFunctionCallDoneEvent(), mockCompletedEvent("r3"))
        every { stream3.close() } returns Unit
        every { finalStream.stream() } returns Stream.of(mockOutputTextEvent("Final Answer"), mockCompletedEvent("r4"))
        every { finalStream.close() } returns Unit

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("web_search", "{}")

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
        )

        val events = service.sendPrompt(
            prompt = "Search 3 times",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.WEB_SEARCH)
            ),
            closeConversation = false,
        ).toList()

        assertEquals("Final Answer", (events.filterIsInstance<InferenceEvent.PartialResponse>().last()).chunk)
        assertEquals(1, events.filterIsInstance<InferenceEvent.Finished>().size)
        coVerify(exactly = 3) { toolExecutor.execute(any()) }
    }

    @Test
    fun `search enabled prompt reapplies system instructions on follow up response request`() = runTest {
        val client = mockk<OpenAIClient>()
        val responseService = mockk<ResponseService>()
        val initialStreamResponse = mockk<StreamResponse<ResponseStreamEvent>>()
        val followUpStreamResponse = mockk<StreamResponse<ResponseStreamEvent>>()
        val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
        val capturedParams = mutableListOf<ResponseCreateParams>()

        every { client.responses() } returns responseService
        every { responseService.createStreaming(capture(capturedParams)) } returnsMany listOf(
            initialStreamResponse,
            followUpStreamResponse,
        )
        every { initialStreamResponse.stream() } returns Stream.of(
            mockFunctionCallDoneEvent(),
            mockCompletedEvent(responseId = "resp_call_1"),
        )
        every { initialStreamResponse.close() } returns Unit
        every { followUpStreamResponse.stream() } returns Stream.of(
            mockOutputTextEvent("Use the search result summary."),
            mockCompletedEvent(responseId = "resp_followup"),
        )
        every { followUpStreamResponse.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
        )
        service.setHistory(listOf(ChatMessage(Role.SYSTEM, "Stay concise and cite sources.")))

        service.sendPrompt(
            prompt = "Find recent Android agent news",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        assertEquals(2, capturedParams.size)
        assertTrue(capturedParams.first().instructions().isPresent)
        assertEquals("Stay concise and cite sources.", capturedParams.first().instructions().get())
        assertTrue(capturedParams[1].instructions().isPresent)
        assertEquals("Stay concise and cite sources.", capturedParams[1].instructions().get())
    }

    @Test
    fun `executePrompt falls back to chat completions on 404 error`() = runTest {
        val client = mockk<OpenAIClient>(relaxed = true)
        val responseService = mockk<ResponseService>(relaxed = true)
        val chatService = mockk<com.openai.services.blocking.ChatService>(relaxed = true)
        val completionService = mockk<com.openai.services.blocking.chat.ChatCompletionService>(relaxed = true)
        val chatStreamResponse = mockk<StreamResponse<ChatCompletionChunk>>(relaxed = true)

        every { client.responses() } returns responseService
        every { client.chat() } returns chatService
        every { chatService.completions() } returns completionService
        every { completionService.createStreaming(any<ChatCompletionCreateParams>()) } returns chatStreamResponse
        
        // Mock 404 error on Responses API
        every { responseService.createStreaming(any<ResponseCreateParams>()) } throws RuntimeException("404 Not Found")

        // Mock successful Chat Completions API
        every { chatStreamResponse.stream() } returns Stream.of(
            mockChatChunk("Falling back to chat."),
        )
        every { chatStreamResponse.close() } returns Unit

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(mockk(), loggingPort),
        )

        val events = service.sendPrompt(
            prompt = "Hello",
            options = GenerationOptions(
                reasoningBudget = 0,
                apiProvider = ApiProvider.OPENAI,
                modelId = "gpt-4o"
            ),
            closeConversation = false,
        ).toList()

        println("Collected events: $events")

        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Falling back to chat." })
        coVerify(exactly = 1) { responseService.createStreaming(any<ResponseCreateParams>()) }
    }

    @Test
    fun `executeToolingPrompt falls back to chat completions tooling on 404 error`() = runTest {
        val client = mockk<OpenAIClient>(relaxed = true)
        val responseService = mockk<ResponseService>(relaxed = true)
        val chatService = mockk<com.openai.services.blocking.ChatService>(relaxed = true)
        val completionService = mockk<com.openai.services.blocking.chat.ChatCompletionService>(relaxed = true)
        val chatStreamResponse = mockk<StreamResponse<ChatCompletionChunk>>(relaxed = true)

        every { client.responses() } returns responseService
        every { client.chat() } returns chatService
        every { chatService.completions() } returns completionService
        every { completionService.createStreaming(any<ChatCompletionCreateParams>()) } returns chatStreamResponse

        // Mock 404 error on Responses API for tooling
        every { responseService.createStreaming(any<ResponseCreateParams>()) } throws RuntimeException("404 Not Found")

        // Mock successful Chat Completions API for tooling fallback
        every { chatStreamResponse.stream() } returns Stream.of(
            mockChatChunk("Falling back to chat tooling."),
        )
        every { chatStreamResponse.close() } returns Unit

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(mockk(), loggingPort),
        )

        val events = service.sendPrompt(
            prompt = "Use tools",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                apiProvider = ApiProvider.OPENAI,
                modelId = "gpt-4o"
            ),
            closeConversation = false,
        ).toList()

        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Falling back to chat tooling." })
        coVerify(exactly = 1) { responseService.createStreaming(any<ResponseCreateParams>()) }
    }

    @Test
    fun `executeToolingPrompt aggregates fragmented native tool calls in chat completions fallback`() = runTest {
        val client = mockk<OpenAIClient>(relaxed = true)
        val responseService = mockk<ResponseService>(relaxed = true)
        val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
        val chatService = mockk<com.openai.services.blocking.ChatService>(relaxed = true)
        val completionService = mockk<com.openai.services.blocking.chat.ChatCompletionService>(relaxed = true)
        val chatStreamResponse1 = mockk<StreamResponse<ChatCompletionChunk>>(relaxed = true)
        val chatStreamResponse2 = mockk<StreamResponse<ChatCompletionChunk>>(relaxed = true)

        every { client.responses() } returns responseService
        every { client.chat() } returns chatService
        every { chatService.completions() } returns completionService
        
        // Fail the first pass to trigger fallback
        every { responseService.createStreaming(any<ResponseCreateParams>()) } throws RuntimeException("404 Not Found")

        // Mock pass 1: Model emits tool call
        every { client.chat().completions().createStreaming(any<ChatCompletionCreateParams>()) } answers {
            val params = firstArg<ChatCompletionCreateParams>()
            if (params.messages().size == 1) chatStreamResponse1 else chatStreamResponse2
        }
        
        every { chatStreamResponse1.stream() } returns Stream.of(
            mockChatToolCallChunk(index = 0, id = "call_abc", name = "web_search", args = "{\"query\":"),
            mockChatToolCallChunk(index = 0, args = "\"android news\"}"),
            mockChatFinishChunk(finishReason = "tool_calls")
        )
        every { chatStreamResponse1.close() } returns Unit

        // Mock tool execution
        coEvery { toolExecutor.execute(match { it.toolName == "web_search" }) } returns ToolExecutionResult(
            toolName = "web_search",
            resultJson = "{\"results\":[]}"
        )

        // Pass 2: Follow-up
        val pass2Matcher: (ChatCompletionCreateParams) -> Boolean = { it.messages().size > 2 }
        every { client.chat().completions().createStreaming(match(pass2Matcher)) } returns chatStreamResponse2
        
        every { chatStreamResponse2.stream() } returns Stream.of(
            mockChatChunk("Here are the results."),
            mockChatFinishChunk(finishReason = "stop")
        )
        every { chatStreamResponse2.close() } returns Unit

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
        )

        val events = service.sendPrompt(
            prompt = "Search news",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                apiProvider = ApiProvider.OPENAI,
                modelId = "gpt-4o"
            ),
            closeConversation = false,
        ).toList()

        // Verify behavioral intent:
        // 1. Fragmented tool call was aggregated
        coVerify(exactly = 1) { 
            toolExecutor.execute(any()) 
        }
        
        // 2. Final response received
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Here are the results." })
    }

    private fun mockChatToolCallChunk(
        index: Long,
        id: String? = null,
        name: String? = null,
        args: String? = null
    ): ChatCompletionChunk {
        val toolCall = mockk<ChatCompletionChunk.Choice.Delta.ToolCall>()
        every { toolCall.index() } returns index
        every { toolCall.id() } returns Optional.ofNullable(id)
        
        val function = mockk<ChatCompletionChunk.Choice.Delta.ToolCall.Function>()
        every { function.name() } returns Optional.ofNullable(name)
        every { function.arguments() } returns Optional.ofNullable(args)
        
        every { toolCall.function() } returns Optional.of(function)

        val delta = mockk<ChatCompletionChunk.Choice.Delta>()
        every { delta.content() } returns Optional.empty()
        every { delta.toolCalls() } returns Optional.of(listOf(toolCall))
        every { delta._additionalProperties() } returns emptyMap()

        val choice = mockk<ChatCompletionChunk.Choice>()
        every { choice.delta() } returns delta
        every { choice.finishReason() } returns Optional.empty()

        val chunk = mockk<ChatCompletionChunk>()
        every { chunk.id() } returns "chat_resp_tool"
        every { chunk.choices() } returns listOf(choice)
        return chunk
    }

    private fun mockChatFinishChunk(finishReason: String): ChatCompletionChunk {
        val delta = mockk<ChatCompletionChunk.Choice.Delta>()
        every { delta.content() } returns Optional.empty()
        every { delta.toolCalls() } returns Optional.empty()
        every { delta._additionalProperties() } returns emptyMap()

        val choice = mockk<ChatCompletionChunk.Choice>()
        every { choice.delta() } returns delta
        every { choice.finishReason() } returns Optional.of(ChatCompletionChunk.Choice.FinishReason.of(finishReason))

        val chunk = mockk<ChatCompletionChunk>()
        every { chunk.id() } returns "chat_resp_finish"
        every { chunk.choices() } returns listOf(choice)
        return chunk
    }

    private fun mockChatChunk(text: String): ChatCompletionChunk {
        val delta = mockk<ChatCompletionChunk.Choice.Delta>()
        every { delta.content() } returns Optional.of(text)
        every { delta.toolCalls() } returns Optional.empty()
        every { delta._additionalProperties() } returns emptyMap()

        val choice = mockk<ChatCompletionChunk.Choice>()
        every { choice.delta() } returns delta
        every { choice.finishReason() } returns Optional.empty()

        val chunk = mockk<ChatCompletionChunk>()
        every { chunk.id() } returns "chat_resp_1"
        every { chunk.choices() } returns listOf(choice)
        return chunk
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
        every { functionCallDone.name() } returns "web_search"
        every { functionCallDone.arguments() } returns """{"query":"latest android tool calling"}"""
        every { functionCallDone.itemId() } returns "call_1"
        val event = mockk<ResponseStreamEvent>(relaxed = true)
        every { event.isFunctionCallArgumentsDone() } returns true
        every { event.functionCallArgumentsDone() } returns Optional.of(functionCallDone)
        return event
    }
}
