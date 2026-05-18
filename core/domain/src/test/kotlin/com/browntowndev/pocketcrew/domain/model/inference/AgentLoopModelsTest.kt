package com.browntowndev.pocketcrew.domain.model.inference

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentLoopModelsTest {
    @Test
    fun `finish boundary hook flags default to disabled`() {
        val config = AgentLoopConfig(
            io = AgentLoopSessionIoParams(
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
        val config = AgentLoopConfig(
            agent = AgentLoopAgentConfig(
                enableLoopHooks = true,
                enablePendingWorkGuard = true,
            ),
            capabilities = AgentLoopSessionCapabilities(
                lifecycleHooksSupported = true,
            ),
            io = AgentLoopSessionIoParams(
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
}
