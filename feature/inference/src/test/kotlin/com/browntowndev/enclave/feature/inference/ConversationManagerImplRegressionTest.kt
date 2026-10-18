// MockK is used only for LiteRT-LM SDK types (com.google.ai.edge.litertlm.*), a third-party SDK boundary.
@file:Suppress("ForbiddenMockKInTests")

package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.AgentPolicyRepairResult
import com.google.ai.edge.litertlm.Conversation
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ConversationManagerImplRegressionTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()

    private fun createManager(policy: AgentPolicyPort? = null): ConversationManagerImpl =
        ConversationManagerImpl(
            context = ApplicationProvider.getApplicationContext<Context>(),
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
            embeddingEnginePort = FakeEmbeddingEngine(),
            agentPolicyPort = policy ?: com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort,
        )

    @Test
    fun `executeToolSafely returns synthetic error when policy disallows tool dispatch`() =
        runTest {
            val manager = createManager(policy = FakeAgentPolicyPort(allowTools = false))

            manager.beginGenerationRequest()
            val result =
                manager.executeToolSafelyForTest(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"latest android tool calling"}""",
                        provider = "LITERT",
                        modelType = ModelType.FAST,
                    ),
                    manager.defaultTurnContextForTest(),
                )

            assertTrue(result.contains("Context window exceeded"))
            assertTrue(toolExecutor.executedRequests.isEmpty())
        }

    @Test
    fun `executeToolSafely feeds policy soft failure detection into later dispatch gate`() =
        runTest {
            val manager = createManager(policy = FakeAgentPolicyPort(maxAllowedStreak = 1u))
            toolExecutor.resultJson = """{"status":"error","message":"provider failed"}"""

            val request =
                ToolCallRequest(
                    toolName = "web_search",
                    argumentsJson = """{"query":"latest android tool calling"}""",
                    provider = "LITERT",
                    modelType = ModelType.FAST,
                )

            manager.beginGenerationRequest()
            val turn = manager.defaultTurnContextForTest()
            val firstResult = manager.executeToolSafelyForTest(request, turn)
            val secondResult = manager.executeToolSafelyForTest(request, turn)

            assertEquals("""{"status":"error","message":"provider failed"}""", firstResult)
            assertTrue(secondResult.contains("Context window exceeded"))
            assertEquals(1, toolExecutor.executedRequests.size)
        }

    @Test
    fun `executeToolSafely does not log error when successful search query is error`() =
        runTest {
            val resultJson = """{"chat_id":"chat-1","query":"error","total_results":0,"returned_results":0,"messages":[]}"""
            toolExecutor.resultJson = resultJson
            val manager = createManager()

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
            assertTrue(
                loggingPort.errorCalls.none {
                    it.message.contains("Native tool call returned error payload")
                },
            )
        }

    @Test
    fun `cancelProcess keeps cancellation latched and delegates to active port`() {
        val activeConversationPort = FakeLiteRtConversation()
        val manager = createManager()

        manager.conversationPort = activeConversationPort

        manager.cancelProcess()

        assertEquals(1, activeConversationPort.cancelProcessCount)
        assertTrue(manager.isCurrentGenerationCancelled)
        assertEquals(1, manager.cancellationEpoch.get())
    }

    @Test
    fun `closeConversation calls native close on active conversation and resets state`() =
        runTest {
            val liteRtConversation = mockk<Conversation>(relaxed = true)
            val manager = createManager()

            manager.conversation = liteRtConversation
            manager.conversationPort = FakeLiteRtConversation()

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
            toolSemanticsJson: String,
            toolCatalogJson: String,
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
