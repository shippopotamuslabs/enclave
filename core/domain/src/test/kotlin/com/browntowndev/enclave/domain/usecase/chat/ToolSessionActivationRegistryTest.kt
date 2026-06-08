package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolSessionActivationRegistryTest {
    @Test
    fun seedTurn_replaces_registry() {
        val registry = ToolSessionActivationRegistry()
        registry.seedTurn(listOf("search_tools", "web_search"))
        registry.applySearchActivations(listOf("append_blocks"), chatId = null)
        registry.seedTurn(listOf("search_tools"))
        assertEquals(listOf("search_tools"), registry.orderedActiveNames())
    }

    @Test
    fun applySearchActivations_appends_and_persists_monotonic() {
        val monotonic = InMemoryMonotonicToolActivationStore()
        var searchCallbackCount = 0
        val registry =
            ToolSessionActivationRegistry(
                monotonicToolActivation = monotonic,
                onSearchDiscoveredNewTools = { searchCallbackCount++ },
            )
        registry.seedTurn(listOf("search_tools"))
        registry.applySearchActivations(listOf("append_blocks"), ChatId("chat-1"))

        assertEquals(
            listOf("search_tools", "append_blocks"),
            registry.orderedActiveNames(),
        )
        assertEquals(setOf("append_blocks"), monotonic.activatedNames(ChatId("chat-1")))
        assertEquals(1, searchCallbackCount)
    }

    @Test
    fun applySearchActivations_is_thread_safe_under_parallel_calls() =
        runTest {
            val registry = ToolSessionActivationRegistry()
            registry.seedTurn(listOf("search_tools"))

            coroutineScope {
                List(16) { index ->
                    async(Dispatchers.Default) {
                        registry.applySearchActivations(listOf("tool_$index"), chatId = null)
                    }
                }.awaitAll()
            }

            val names = registry.orderedActiveNames()
            assertEquals(ToolSearchThresholds.MAX_ACTIVATED_TOOLS_PER_SESSION, names.size)
            assertEquals("search_tools", names.first())
            assertTrue(names.drop(1).all { it.startsWith("tool_") })
            assertEquals(names.size, names.toSet().size)
        }

    @Test
    fun applySearchActivations_respects_session_cap() {
        val registry = ToolSessionActivationRegistry()
        registry.seedTurn(emptyList())
        registry.applySearchActivations((1..30).map { "tool_$it" }, chatId = null)
        assertEquals(ToolSearchThresholds.MAX_ACTIVATED_TOOLS_PER_SESSION, registry.orderedActiveNames().size)
    }

    @Test
    fun applySearchActivations_no_op_when_empty() {
        var changed = false
        val registry =
            ToolSessionActivationRegistry(
                onSearchDiscoveredNewTools = { changed = true },
            )
        registry.seedTurn(listOf("search_tools"))
        registry.applySearchActivations(emptyList(), chatId = null)
        assertFalse(changed)
    }
}
