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
import com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract for LiteRT generation request ownership: an older request draining its
 * `finally` block must not cancel or epoch-bump state owned by a newer request.
 */
class LiteRtActiveGenerationContractTest {
    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    private fun manager(policy: AgentPolicyPort = NoopAgentPolicyPort): ConversationManagerImpl =
        ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
            embeddingEnginePort = mockk(relaxed = true),
            agentPolicyPort = policy,
        )

    @Test
    fun finishedBackgroundRequestDoesNotCancelNewerRequest() {
        val manager = manager()
        val request1 = manager.beginGenerationRequest()
        val request2 = manager.beginGenerationRequest()

        manager.endGenerationRequest(request1)

        assertEquals(request2, manager.currentRequestId)
        assertFalse(manager.isCurrentGenerationCancelled)
        assertEquals(0, manager.cancellationEpoch.get())
    }

    @Test
    fun interleavedFinallyOfOlderRequestLeavesNewerUntouched() {
        val manager = manager()
        val request1 = manager.beginGenerationRequest()
        val request2 = manager.beginGenerationRequest()

        manager.endGenerationRequest(request1)

        assertEquals(request2, manager.currentRequestId)
        assertFalse(manager.isCurrentGenerationCancelled)
    }

    @Test
    fun softFailureStreakResetsPerGenerationRequest() =
        runTest {
            val policy = FakeAgentPolicyPort(maxAllowedStreak = 1u)
            val manager = manager(policy)
            coEvery { toolExecutor.execute(any()) } returns
                ToolExecutionResult(
                    toolName = "web_search",
                    resultJson = """{"status":"error","message":"provider failed"}""",
                )

            manager.beginGenerationRequest()
            val turn = manager.defaultTurnContextForTest()
            val first =
                manager.executeToolSafelyForTest(liteRtSearchRequest(), turn)
            val gated =
                manager.executeToolSafelyForTest(liteRtSearchRequest(), turn)
            assertEquals("""{"status":"error","message":"provider failed"}""", first)
            assertTrue(gated.contains("Context window exceeded"))

            manager.beginGenerationRequest()
            val afterReset =
                manager.executeToolSafelyForTest(
                    liteRtSearchRequest(),
                    manager.defaultTurnContextForTest(),
                )
            assertEquals("""{"status":"error","message":"provider failed"}""", afterReset)
        }

    @Test
    fun cancelledToolDispatchMarksConversationDirty() =
        runTest {
            val manager = manager()
            val request1 = manager.beginGenerationRequest()
            val staleTurn = manager.defaultTurnContextForTest(request1)
            manager.beginGenerationRequest()

            try {
                manager.executeToolSafelyForTest(liteRtSearchRequest(), staleTurn)
                org.junit.jupiter.api.Assertions.fail("Expected CancellationException")
            } catch (_: CancellationException) {
                // expected
            }
            assertTrue(manager.hasTransientToolResults)
        }

    private fun liteRtSearchRequest(): ToolCallRequest =
        ToolCallRequest(
            toolName = "web_search",
            argumentsJson = """{"query":"latest android tool calling"}""",
            provider = "LITERT",
            modelType = ModelType.FAST,
        )

    private class FakeAgentPolicyPort(
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
        ): Boolean = softFailureStreak < maxAllowedStreak

        override fun resultJsonLooksSoftFailure(json: String): Boolean = json.contains("\"status\":\"error\"")
    }
}