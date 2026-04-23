package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.model.inference.SamplerConfig
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun `cancelProcess keeps cancellation latched and delegates to active port`() {
        val activeConversationPort = mockk<LiteRtConversation>(relaxed = true)
        val manager = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )

        setPrivateField(manager, "conversationPort", activeConversationPort)

        manager.cancelProcess()

        verify(exactly = 1) { activeConversationPort.cancelProcess() }
        assertTrue(getPrivateField<Boolean>(manager, "isCurrentGenerationCancelled"))
        assertEquals(1, getPrivateField<AtomicInteger>(manager, "cancellationEpoch").get())
    }

    @Test
    fun `closeConversation calls native close on active conversation and resets state`() = runTest {
        val liteRtConversation = mockk<Conversation>(relaxed = true)
        val manager = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )

        setPrivateField(manager, "conversation", liteRtConversation)
        setPrivateField(manager, "conversationPort", mockk<LiteRtConversation>(relaxed = true))

        manager.closeConversation()

        verify(exactly = 1) { liteRtConversation.close() }
        assertEquals(null, getPrivateField<Conversation?>(manager, "conversation"))
        assertEquals(null, getPrivateField<Any?>(manager, "conversationPort"))
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(target: Any, fieldName: String): T {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as T
    }
}
