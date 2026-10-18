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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ConversationManagerImplPolicyBridgeTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()

    @Test
    fun shouldAllowTool_falseGatesLiteRtDispatch() =
        runTest {
            val manager = createManager(policy = FakeAgentPolicyPort(allowTools = false))

            manager.beginGenerationRequest()
            val result =
                manager.executeToolSafelyForTest(
                    liteRtSearchRequest(),
                    manager.defaultTurnContextForTest(),
                )

            assertTrue(result.contains("Context window exceeded"))
            assertTrue(toolExecutor.executedRequests.isEmpty())
        }

    @Test
    fun resultJsonLooksSoftFailure_incrementsStreakForLaterGate() =
        runTest {
            val policy = FakeAgentPolicyPort(maxAllowedStreak = 1u)
            val manager = createManager(policy)
            toolExecutor.resultJson = """{"status":"error","message":"provider failed"}"""

            manager.beginGenerationRequest()
            val turn = manager.defaultTurnContextForTest()
            val firstResult = manager.executeToolSafelyForTest(liteRtSearchRequest(), turn)
            val secondResult = manager.executeToolSafelyForTest(liteRtSearchRequest(), turn)

            assertEquals("""{"status":"error","message":"provider failed"}""", firstResult)
            assertTrue(secondResult.contains("Context window exceeded"))
            assertEquals(1, toolExecutor.executedRequests.size)
        }

    private fun createManager(policy: AgentPolicyPort): ConversationManagerImpl =
        ConversationManagerImpl(
            context = ApplicationProvider.getApplicationContext<Context>(),
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
            embeddingEnginePort = FakeEmbeddingEngine(),
            agentPolicyPort = policy,
        )

    private fun liteRtSearchRequest(): ToolCallRequest =
        ToolCallRequest(
            toolName = "web_search",
            argumentsJson = """{"query":"latest android tool calling"}""",
            provider = "LITERT",
            modelType = ModelType.FAST,
        )

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
