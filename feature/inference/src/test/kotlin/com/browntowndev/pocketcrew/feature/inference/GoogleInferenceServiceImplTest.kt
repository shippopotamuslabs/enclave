package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.ResponseStream
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.stream.Stream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleInferenceServiceImplTest {

    @Test
    fun `search enabled prompt completes one Google tool round trip before final assistant text`() = runTest {
        val client = mockk<Client>()
        val models = mockk<Models>()
        val initialStream = mockk<ResponseStream<GenerateContentResponse>>()
        val followUpStream = mockk<ResponseStream<GenerateContentResponse>>()
        val toolExecutor = mockk<ToolExecutorPort>()
        every { models.generateContentStream(any(), any<List<Content>>(), any<GenerateContentConfig>()) } returnsMany listOf(
            initialStream,
            followUpStream,
        )
        every { initialStream.iterator() } returns Stream.of(
            thinkingResponse("Need to search first."),
            functionCallResponse(),
        ).iterator()
        every { followUpStream.iterator() } returns Stream.of(
            thinkingResponse("Reviewing search result."),
            textResponse("Use the search result summary."),
        ).iterator()
        every { initialStream.close() } returns Unit
        every { followUpStream.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = GoogleInferenceServiceImpl(
            client = client,
            explicitModelsApi = models,
            modelId = "gemini-2.5-flash",
            modelType = ModelType.FAST,
            loggingPort = mockk(relaxed = true),
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
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Reviewing search result." })
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Use the search result summary." })
        assertTrue(events.any { it is InferenceEvent.Finished && it.modelType == ModelType.FAST })
        coVerify(exactly = 1) { toolExecutor.execute(any()) }
    }

    @Test
    fun `search enabled Google prompt does not silently fall back when tool path fails before final text`() = runTest {
        val client = mockk<Client>()
        val models = mockk<Models>()
        every { models.generateContentStream(any(), any<List<Content>>(), any<GenerateContentConfig>()) } throws RuntimeException("boom")

        val service = GoogleInferenceServiceImpl(
            client = client,
            explicitModelsApi = models,
            modelId = "gemini-2.5-flash",
            modelType = ModelType.FAST,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            toolExecutor = mockk(relaxed = true),
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

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Google tool execution failed before final response", error.cause.message)
    }

    @Test
    fun `search enabled Google prompt rejects recursive second tool request`() = runTest {
        val client = mockk<Client>()
        val models = mockk<Models>()
        val initialStream = mockk<ResponseStream<GenerateContentResponse>>()
        val followUpStream = mockk<ResponseStream<GenerateContentResponse>>()
        val toolExecutor = mockk<ToolExecutorPort>()
        every { models.generateContentStream(any(), any<List<Content>>(), any<GenerateContentConfig>()) } returnsMany listOf(
            initialStream,
            followUpStream,
        )
        every { initialStream.iterator() } returns Stream.of(functionCallResponse()).iterator()
        every { followUpStream.iterator() } returns Stream.of(functionCallResponse()).iterator()
        every { initialStream.close() } returns Unit
        every { followUpStream.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = GoogleInferenceServiceImpl(
            client = client,
            explicitModelsApi = models,
            modelId = "gemini-2.5-flash",
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

    private fun functionCallResponse(): GenerateContentResponse =
        GenerateContentResponse.builder()
            .candidates(
                Candidate.builder()
                    .content(
                        Content.builder()
                            .role("model")
                            .parts(
                                listOf(
                                    Part.fromFunctionCall(
                                        "tavily_web_search",
                                        mapOf("query" to "latest android tool calling"),
                                    )
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun textResponse(text: String): GenerateContentResponse =
        GenerateContentResponse.builder()
            .candidates(
                Candidate.builder()
                    .content(
                        Content.builder()
                            .role("model")
                            .parts(listOf(Part.fromText(text)))
                            .build()
                    )
                    .build()
            )
            .build()

    private fun thinkingResponse(text: String): GenerateContentResponse =
        GenerateContentResponse.builder()
            .candidates(
                Candidate.builder()
                    .content(
                        Content.builder()
                            .role("model")
                            .parts(
                                listOf(
                                    Part.builder()
                                        .text(text)
                                        .thought(true)
                                        .build()
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .build()
}
