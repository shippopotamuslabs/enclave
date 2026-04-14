package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.Part
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleInferenceServiceImplTest {

    @Test
    fun `search enabled prompt completes one Google tool round trip before final assistant text`() = runTest {
        val sdkClient = mockk<GoogleGenAiSdkClient>()
        val toolExecutor = mockk<ToolExecutorPort>()
        
        val responses = mutableListOf<suspend (suspend (InferenceEvent) -> Unit) -> GoogleSdkResult>(
            { emit ->
                emit(InferenceEvent.Thinking("Need to search first.", ModelType.FAST))
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = FunctionCall.builder()
                        .name("tavily_web_search")
                        .args(mapOf("query" to "latest android tool calling"))
                        .build(),
                    assistantContent = Content.builder().build(),
                )
            },
            { emit ->
                emit(InferenceEvent.Thinking("Reviewing search result.", ModelType.FAST))
                emit(InferenceEvent.PartialResponse("Use the search result summary.", ModelType.FAST))
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = Content.builder().build(),
                )
            }
        )

        coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } coAnswers {
            val emitEvent = it.invocation.args[4] as suspend (InferenceEvent) -> Unit
            responses.removeAt(0).invoke(emitEvent)
        }

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = GoogleInferenceServiceImpl(
            sdkClient = sdkClient,
            modelId = "gemini-2.5-flash",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
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
        coVerify(exactly = 1) { toolExecutor.execute(any()) }
    }

    @Test
    fun `search enabled Google prompt does not silently fall back when tool path fails before final text`() = runTest {
        val sdkClient = mockk<GoogleGenAiSdkClient>()
        coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } throws RuntimeException("boom")

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = GoogleInferenceServiceImpl(
            sdkClient = sdkClient,
            modelId = "gemini-2.5-flash",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(mockk(relaxed = true), loggingPort),
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
        val message = error.cause.message ?: ""
        // The service wraps exceptions in IllegalStateException with a custom message
        assertTrue(message.contains("Google tool execution failed before final response"), "Actual message: $message")
        assertEquals("boom", error.cause.cause?.message)
    }

    @Test
    fun `search enabled Google prompt rejects recursive second tool request`() = runTest {
        val sdkClient = mockk<GoogleGenAiSdkClient>()
        val toolExecutor = mockk<ToolExecutorPort>()
        
        coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } coAnswers {
            GoogleSdkResult(
                emittedAny = true,
                functionCall = FunctionCall.builder()
                    .name("tavily_web_search")
                    .args(mapOf("query" to "latest android tool calling"))
                    .build(),
                assistantContent = Content.builder().build(),
            )
        }

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val service = GoogleInferenceServiceImpl(
            sdkClient = sdkClient,
            modelId = "gemini-2.5-flash",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
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
}
