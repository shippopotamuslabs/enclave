package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchToolsHostCoordinatorTest {
    @Test
    fun executeFromArguments_resolves_search_and_updates_registry() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val coordinator = SearchToolsHostCoordinator(embeddingEngine)
            val registry = ToolSessionActivationRegistry()
            registry.seedTurn(listOf(ToolDefinition.SEARCH_TOOLS.name))

            val result =
                coordinator.executeFromArguments(
                    argumentsJson = """{"capability":"append_blocks","limit":"3"}""",
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activationRegistry = registry,
                    providedIndex = ToolSearchTestFixtures.searchIndex(),
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                    chatId = null,
                )

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("ok", json["status"]?.jsonPrimitive?.content)
            assertTrue(result.activatedToolNames.contains("append_blocks"))
            assertEquals(
                listOf("tool_discovery_tool", "append_blocks"),
                registry.orderedActiveNames(),
            )
        }

    @Test
    fun execute_persists_activations_to_monotonic_when_chat_id_set() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("document writing") } returns
                ToolSearchTestFixtures.queryEmbeddingDocumentWriting()
            val monotonic = InMemoryMonotonicToolActivationStore()
            val registry =
                ToolSessionActivationRegistry(monotonicToolActivation = monotonic)
            registry.seedTurn(listOf(ToolDefinition.SEARCH_TOOLS.name))
            val coordinator = SearchToolsHostCoordinator(embeddingEngine)

            coordinator.execute(
                SearchToolsHostInput(
                    query = "document writing",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activationRegistry = registry,
                    providedIndex = ToolSearchTestFixtures.searchIndex(),
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                    chatId = ChatId("chat-coordinator"),
                ),
            )

            assertEquals(
                setOf("append_blocks"),
                monotonic.activatedNames(ChatId("chat-coordinator")),
            )
        }

    @Test
    fun inputFrom_parses_arguments_json() {
        val registry = ToolSessionActivationRegistry()
        val input =
            SearchToolsHostCoordinator.inputFrom(
                argumentsJson = """{"capability":"read_context","limit":"2"}""",
                toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                activationRegistry = registry,
                providedIndex = null,
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
                chatId = ChatId("c1"),
            )

        assertEquals("read_context", input.query)
        assertEquals(2, input.limit)
        assertEquals(ChatId("c1"), input.chatId)
    }

    @Test
    fun inputFrom_parses_capability_key() {
        val registry = ToolSessionActivationRegistry()
        val input =
            SearchToolsHostCoordinator.inputFrom(
                argumentsJson = """{"capability":"send email","limit":"2"}""",
                toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                activationRegistry = registry,
                providedIndex = null,
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
                chatId = ChatId("c2"),
            )

        assertEquals("send email", input.query)
        assertEquals(2, input.limit)
    }

    @Test
    fun inputFrom_rejects_legacy_query_key() {
        val registry = ToolSessionActivationRegistry()
        val input =
            SearchToolsHostCoordinator.inputFrom(
                argumentsJson = """{"query":"send email","limit":"1"}""",
                toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                activationRegistry = registry,
                providedIndex = null,
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
                chatId = ChatId("c3"),
            )

        assertEquals("", input.query)
        assertEquals(1, input.limit)
    }
}
