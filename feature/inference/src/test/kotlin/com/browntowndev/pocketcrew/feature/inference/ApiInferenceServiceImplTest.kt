package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.FakeToolStreamGatePort
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
            toolStreamGatePort = FakeToolStreamGatePort(),
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
            toolStreamGatePort = FakeToolStreamGatePort(),
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
            toolStreamGatePort = FakeToolStreamGatePort(),
        )

        val history = listOf(ChatMessage(Role.USER, "hello"))
        val merged = service.mergeSystemPrompt(history, "   ")

        assertEquals(history, merged)
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
            toolStreamGatePort = FakeToolStreamGatePort(),
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
