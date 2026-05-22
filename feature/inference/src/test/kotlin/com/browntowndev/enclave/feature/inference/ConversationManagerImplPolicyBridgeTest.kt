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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationManagerImplPolicyBridgeTest {
    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    @Test
    fun shouldAllowTool_falseGatesLiteRtDispatch() =
        runTest {
            val manager = createManager(policy = FakeAgentPolicyPort(allowTools = false))

            val result = manager.executeToolSafelyForTest(liteRtSearchRequest())

            assertTrue(result.contains("Context window exceeded"))
            coVerify(exactly = 0) { toolExecutor.execute(any()) }
        }

    @Test
    fun resultJsonLooksSoftFailure_incrementsStreakForLaterGate() =
        runTest {
            val policy = FakeAgentPolicyPort(maxAllowedStreak = 1u)
            val manager = createManager(policy)
            coEvery { toolExecutor.execute(any()) } returns
                ToolExecutionResult(
                    toolName = "web_search",
                    resultJson = """{"status":"error","message":"provider failed"}""",
                )

            val firstResult = manager.executeToolSafelyForTest(liteRtSearchRequest())
            val secondResult = manager.executeToolSafelyForTest(liteRtSearchRequest())

            assertEquals("""{"status":"error","message":"provider failed"}""", firstResult)
            assertTrue(secondResult.contains("Context window exceeded"))
            coVerify(exactly = 1) { toolExecutor.execute(any()) }
        }

    private fun createManager(policy: AgentPolicyPort): ConversationManagerImpl =
        ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
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
