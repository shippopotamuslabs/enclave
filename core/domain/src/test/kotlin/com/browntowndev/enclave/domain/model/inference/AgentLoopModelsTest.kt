package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentLoopModelsTest {
    @Test
    fun `finish boundary hook flags default to disabled`() {
        val config =
            AgentLoopConfig(
                io =
                    AgentLoopSessionIoParams(
                        provider = "TEST",
                        contextWindowTokens = 128_000u,
                        toolsSchemaJson = "[]",
                        systemPrompt = "system",
                    ),
            )

        assertFalse(config.agent.enableLoopHooks)
        assertFalse(config.agent.enablePendingWorkGuard)
        assertFalse(config.capabilities.lifecycleHooksSupported)
    }

    @Test
    fun `finish boundary hook flags can be enabled by Kotlin callers`() {
        val config =
            AgentLoopConfig(
                agent =
                    AgentLoopAgentConfig(
                        enableLoopHooks = true,
                        enablePendingWorkGuard = true,
                    ),
                capabilities =
                    AgentLoopSessionCapabilities(
                        lifecycleHooksSupported = true,
                    ),
                io =
                    AgentLoopSessionIoParams(
                        provider = "TEST",
                        contextWindowTokens = 128_000u,
                        toolsSchemaJson = "[]",
                        systemPrompt = "system",
                    ),
            )

        assertTrue(config.agent.enableLoopHooks)
        assertTrue(config.agent.enablePendingWorkGuard)
        assertTrue(config.capabilities.lifecycleHooksSupported)
    }

    @Test
    fun `agent defaults leave adaptive Rust tier policy available`() {
        val agent = AgentLoopAgentConfig()

        assertEquals(0u, agent.promptCacheAnchorThresholdTokens)
        assertEquals(0u, agent.longLoop.compactEveryNToolTurns)
        assertEquals(0u, agent.longLoop.summarizeAfterToolTurns)
        assertEquals(null, agent.preflightTokenThreshold)
        assertEquals(131072uL, agent.toolResultBudgetRelaxedBytes)
    }

    @Test
    fun `chat message metadata is preserved for agent loop rehydration`() {
        val messages =
            listOf(
                ChatMessage(
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson = """{"provider_reasoning":{"reasoning_content":"encrypted"}}""",
                ),
            )

        val agentMessages = messages.toAgentLoopMessages()

        assertEquals("""{"provider_reasoning":{"reasoning_content":"encrypted"}}""", agentMessages.single().metadataJson)
    }
}
