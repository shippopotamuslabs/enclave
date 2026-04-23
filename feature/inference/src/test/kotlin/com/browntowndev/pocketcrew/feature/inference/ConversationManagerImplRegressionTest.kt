package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationManagerImplRegressionTest {

    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    @Test
    fun `executeToolSafely returns stop-tools payload after LiteRT context warning`() = runTest {
        val manager = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )
        manager.forceContextFullWarnedForTest(true)

        val result = manager.executeToolSafelyForTest(
            ToolCallRequest(
                toolName = "tavily_web_search",
                argumentsJson = """{"query":"latest android tool calling"}""",
                provider = "LITERT",
                modelType = ModelType.FAST,
            )
        )

        assertTrue(result.contains("tool_execution_failed"))
        assertTrue(result.contains("Context window exceeded"))
        coVerify(exactly = 0) { toolExecutor.execute(any()) }
    }

    @Test
    fun `executeToolSafely does not log error when successful search query is error`() = runTest {
        val resultJson = """{"chat_id":"chat-1","query":"error","total_results":0,"returned_results":0,"messages":[]}"""
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "search_chat",
            resultJson = resultJson,
        )
        val manager = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )

        val result = manager.executeToolSafelyForTest(
            ToolCallRequest(
                toolName = "search_chat",
                argumentsJson = """{"chat_id":"chat-1","query":"error"}""",
                provider = "LITERT",
                modelType = ModelType.FAST,
            )
        )

        assertEquals(resultJson, result)
        verify(exactly = 0) {
            loggingPort.error(
                any(),
                match { it.contains("Native tool call returned error payload") },
                any(),
            )
        }
    }
}
