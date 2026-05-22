package com.browntowndev.enclave.feature.inference

import android.content.Context
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.AgentPolicyRepairResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
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
    fun `executeToolSafely returns synthetic error when policy disallows tool dispatch`() =
        runTest {
            val policy = FakeAgentPolicyPort(allowTools = false)
            val manager =
                ConversationManagerImpl(
                    context = context,
                    localModelRepository = localModelRepository,
                    activeModelProvider = activeModelProvider,
                    loggingPort = loggingPort,
                    toolExecutor = toolExecutor,
                    agentPolicyPort = policy,
                )

            val result =
                manager.executeToolSafelyForTest(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"latest android tool calling"}""",
                        provider = "LITERT",
                        modelType = ModelType.FAST,
                    ),
                )

            assertTrue(result.contains("Context window exceeded"))
            coVerify(exactly = 0) { toolExecutor.execute(any()) }
        }

    @Test
    fun `executeToolSafely feeds policy soft failure detection into later dispatch gate`() =
        runTest {
            val policy = FakeAgentPolicyPort(maxAllowedStreak = 1u)
            val manager =
                ConversationManagerImpl(
                    context = context,
                    localModelRepository = localModelRepository,
                    activeModelProvider = activeModelProvider,
                    loggingPort = loggingPort,
                    toolExecutor = toolExecutor,
                    agentPolicyPort = policy,
                )
            coEvery { toolExecutor.execute(any()) } returns
                ToolExecutionResult(
                    toolName = "web_search",
                    resultJson = """{"status":"error","message":"provider failed"}""",
                )

            val request =
                ToolCallRequest(
                    toolName = "web_search",
                    argumentsJson = """{"query":"latest android tool calling"}""",
                    provider = "LITERT",
                    modelType = ModelType.FAST,
                )

            val firstResult = manager.executeToolSafelyForTest(request)
            val secondResult = manager.executeToolSafelyForTest(request)

            assertEquals("""{"status":"error","message":"provider failed"}""", firstResult)
            assertTrue(secondResult.contains("Context window exceeded"))
            coVerify(exactly = 1) { toolExecutor.execute(any()) }
        }

    @Test
    fun `executeToolSafely does not log error when successful search query is error`() =
        runTest {
            val resultJson = """{"chat_id":"chat-1","query":"error","total_results":0,"returned_results":0,"messages":[]}"""
            coEvery { toolExecutor.execute(any()) } returns
                ToolExecutionResult(
                    toolName = "search_chat",
                    resultJson = resultJson,
                )
            val manager =
                ConversationManagerImpl(
                    context = context,
                    localModelRepository = localModelRepository,
                    activeModelProvider = activeModelProvider,
                    loggingPort = loggingPort,
                    toolExecutor = toolExecutor,
                )

            val result =
                manager.executeToolSafelyForTest(
                    ToolCallRequest(
                        toolName = "search_chat",
                        argumentsJson = """{"chat_id":"chat-1","query":"error"}""",
                        provider = "LITERT",
                        modelType = ModelType.FAST,
                    ),
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
        val manager =
            ConversationManagerImpl(
                context = context,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                loggingPort = loggingPort,
                toolExecutor = toolExecutor,
            )

        manager.conversationPort = activeConversationPort

        manager.cancelProcess()

        verify(exactly = 1) { activeConversationPort.cancelProcess() }
        assertTrue(manager.isCurrentGenerationCancelled)
        assertEquals(1, manager.cancellationEpoch.get())
    }

    @Test
    fun `closeConversation calls native close on active conversation and resets state`() =
        runTest {
            val liteRtConversation = mockk<Conversation>(relaxed = true)
            val manager =
                ConversationManagerImpl(
                    context = context,
                    localModelRepository = localModelRepository,
                    activeModelProvider = activeModelProvider,
                    loggingPort = loggingPort,
                    toolExecutor = toolExecutor,
                )

            manager.conversation = liteRtConversation
            manager.conversationPort = mockk<LiteRtConversation>(relaxed = true)

            manager.closeConversation()

            verify(exactly = 1) { liteRtConversation.close() }
            assertEquals(null, manager.conversation)
            assertEquals(null, manager.conversationPort)
        }

    private class FakeAgentPolicyPort(
        private val allowTools: Boolean = true,
        private val maxAllowedStreak: UInt = UInt.MAX_VALUE,
    ) : AgentPolicyPort {
        override fun repairTool(
            name: String,
            argsJson: String,
        ): AgentPolicyRepairResult = AgentPolicyRepairResult(resolvedName = name, argumentsJson = argsJson, confidence = 0.0f)

        override fun truncateResult(
            json: String,
            budgetBytes: ULong,
        ): String = json.take(budgetBytes.toInt())

        override fun shouldAllowTool(
            softFailureStreak: UInt,
            maxStreak: UInt,
        ): Boolean = allowTools && softFailureStreak < maxAllowedStreak

        override fun resultJsonLooksSoftFailure(json: String): Boolean = json.contains("\"status\":\"error\"")
    }
}
