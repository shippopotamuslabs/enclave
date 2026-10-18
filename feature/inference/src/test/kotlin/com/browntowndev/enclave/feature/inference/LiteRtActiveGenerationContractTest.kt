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
import com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Contract for LiteRT generation request ownership: an older request draining its
 * `finally` block must not cancel or epoch-bump state owned by a newer request.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LiteRtActiveGenerationContractTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()

    private fun manager(policy: AgentPolicyPort = NoopAgentPolicyPort): ConversationManagerImpl =
        ConversationManagerImpl(
            context = ApplicationProvider.getApplicationContext<Context>(),
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
            embeddingEnginePort = FakeEmbeddingEngine(),
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
            toolExecutor.resultJson = """{"status":"error","message":"provider failed"}"""

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
