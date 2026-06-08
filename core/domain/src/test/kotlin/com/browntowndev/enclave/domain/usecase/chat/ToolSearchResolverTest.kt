package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolSearchResolverTest {
    private val resolver = ToolSearchResolver

    @Test
    fun `TSD-04 resolver BM25 ranks append_blocks for name-like query without embedding`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "append_blocks",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val matches = json["matches"]?.jsonArray.orEmpty()

            assertAll(
                { assertEquals("ok", json["status"]?.jsonPrimitive?.content) },
                { assertEquals("append_blocks", matches.first().jsonObject["name"]?.jsonPrimitive?.content) },
                { assertEquals("bm25", matches.first().jsonObject["match_method"]?.jsonPrimitive?.content) },
                { coVerify(exactly = 0) { embeddingEngine.getEmbedding(any()) } },
            )
        }

    @Test
    fun `TSD-07 embedding fallback activates when BM25 returns no hits`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("document writing") } returns
                ToolSearchTestFixtures.queryEmbeddingDocumentWriting()

            val result =
                resolver.resolve(
                    query = "document writing",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val matches = json["matches"]?.jsonArray.orEmpty()

            assertAll(
                { coVerify(exactly = 1) { embeddingEngine.getEmbedding("document writing") } },
                { assertEquals("append_blocks", matches.first().jsonObject["name"]?.jsonPrimitive?.content) },
                { assertEquals("embedding", matches.first().jsonObject["match_method"]?.jsonPrimitive?.content) },
                { assertEquals(listOf("append_blocks"), result.activatedToolNames) },
            )
        }

    @Test
    fun `TSD-09 search_tools populates activatedToolNames for deferred tool`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "append_blocks",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools =
                        listOf(
                            ToolDefinition.SEARCH_TOOLS,
                            ToolDefinition.WEB_SEARCH,
                            ToolDefinition.WEB_EXTRACT,
                        ),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { assertEquals("ok", json["status"]?.jsonPrimitive?.content) },
                { assertEquals(listOf("append_blocks"), result.activatedToolNames) },
                {
                    assertEquals(
                        listOf("append_blocks"),
                        json["activated_tools"]?.jsonArray?.map { it.jsonPrimitive.content },
                    )
                },
            )
        }

    @Test
    fun `TSD-10 success payload exposes ranked compact matches`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "append_blocks",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val match = json["matches"]?.jsonArray?.first()?.jsonObject
            val tool = json["tools"]?.jsonArray?.first()?.jsonObject

            assertAll(
                { assertEquals("append_blocks", match?.get("name")?.jsonPrimitive?.content) },
                { assertEquals("bm25", match?.get("match_method")?.jsonPrimitive?.content) },
                { assertTrue((match?.get("score")?.jsonPrimitive?.content?.toDouble() ?: 0.0) > 0.0) },
                { assertEquals("append_blocks", tool?.get("name")?.jsonPrimitive?.content) },
                { assertTrue(tool?.get("call_example")?.jsonPrimitive?.content?.contains("append_blocks") == true) },
            )
        }

    @Test
    fun `TSD-15 already active tool is reported not re-activated`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "append_blocks",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS, ToolDefinition.APPEND_BLOCKS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { assertEquals(emptyList<String>(), result.activatedToolNames) },
                {
                    assertEquals(
                        listOf("append_blocks"),
                        json["already_active_tools"]?.jsonArray?.map { it.jsonPrimitive.content },
                    )
                },
                {
                    assertEquals(
                        emptyList<String>(),
                        json["activated_tools"]?.jsonArray?.map { it.jsonPrimitive.content },
                    )
                },
                { assertEquals(0, json["tools"]?.jsonArray?.size ?: 0) },
            )
        }

    @Test
    fun `TSD-17 empty query returns exact error payload`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "   ",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { assertEquals("error", json["status"]?.jsonPrimitive?.content) },
                {
                    assertEquals(
                        ToolSearchThresholds.EMPTY_QUERY_ERROR_MESSAGE,
                        json["message"]?.jsonPrimitive?.content,
                    )
                },
                { assertEquals(emptyList<String>(), result.activatedToolNames) },
                { coVerify(exactly = 0) { embeddingEngine.getEmbedding(any()) } },
            )
        }

    @Test
    fun `TSD-18 no matches returns ok with empty arrays`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("unrelated quantum") } returns
                ToolSearchTestFixtures.queryEmbeddingUnrelatedQuantum()

            val result =
                resolver.resolve(
                    query = "unrelated quantum",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { assertEquals("ok", json["status"]?.jsonPrimitive?.content) },
                { assertEquals(0, json["matches"]?.jsonArray?.size ?: 0) },
                { assertEquals(0, json["activated_tools"]?.jsonArray?.size ?: 0) },
                { assertEquals(emptyList<String>(), result.activatedToolNames) },
            )
        }

    @Test
    fun `TSD-19 sub-threshold embedding does not activate`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("unrelated quantum") } returns
                ToolSearchTestFixtures.queryEmbeddingUnrelatedQuantum()

            val result =
                resolver.resolve(
                    query = "unrelated quantum",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )

            assertAll(
                { coVerify(exactly = 1) { embeddingEngine.getEmbedding("unrelated quantum") } },
                { assertEquals(emptyList<String>(), result.activatedToolNames) },
                {
                    assertEquals(
                        0,
                        Json
                            .parseToJsonElement(result.resultJson)
                            .jsonObject["matches"]
                            ?.jsonArray
                            ?.size ?: 0,
                    )
                },
            )
        }

    @Test
    fun `TSD-08 embedding fallback runs when top BM25 score is below tau_bm25`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("doc") } returns
                ToolSearchTestFixtures.queryEmbeddingDocumentWriting()

            val result =
                resolver.resolve(
                    query = "doc",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { coVerify(exactly = 1) { embeddingEngine.getEmbedding("doc") } },
                {
                    assertEquals(
                        "embedding",
                        json["matches"]?.jsonArray?.first()?.jsonObject?.get("match_method")?.jsonPrimitive?.content,
                    )
                },
            )
        }

    @Test
    fun `TSD-25 limit parameter caps matches and activations`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns
                ToolSearchTestFixtures.genericTestEmbedding()
            val result =
                resolver.resolve(
                    query = "search",
                    limit = 1,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { assertEquals(1, json["matches"]?.jsonArray?.size ?: 0) },
                { assertTrue(result.activatedToolNames.size <= 1) },
            )
        }

    @Test
    fun `TSD-24 duplicate activation names dedupe in first-seen order`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "append_blocks append_blocks",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )

            assertEquals(listOf("append_blocks"), result.activatedToolNames)
        }

    @Test
    fun `LiteRT strategy excludes tools without native providers`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns
                ToolSearchTestFixtures.controlledEmbeddingMap()["read_context_reference"]!!
            val result =
                resolver.resolve(
                    query = "read_context_reference",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.LITE_RT_NATIVE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject

            assertAll(
                { assertTrue(result.activatedToolNames.isEmpty()) },
                { assertTrue(json["matches"]?.jsonArray.orEmpty().isEmpty()) },
            )
        }

    @Test
    fun `MUT-04 embedding fallback skipped when BM25 top score meets threshold`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
            val result =
                resolver.resolve(
                    query = "append_blocks",
                    limit = 5,
                    toolCatalog = ToolSearchTestFixtures.bm25Catalog,
                    activeTools = listOf(ToolDefinition.SEARCH_TOOLS),
                    searchIndex = ToolSearchTestFixtures.searchIndex(),
                    embeddingEngine = embeddingEngine,
                    strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
                )
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val topScore =
                json["matches"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("score")
                    ?.jsonPrimitive
                    ?.content
                    ?.toDouble() ?: 0.0

            assertAll(
                { assertTrue(topScore >= ToolSearchThresholds.TAU_BM25) },
                { coVerify(exactly = 0) { embeddingEngine.getEmbedding(any()) } },
                {
                    assertEquals(
                        "bm25",
                        json["matches"]?.jsonArray?.first()?.jsonObject?.get("match_method")?.jsonPrimitive?.content,
                    )
                },
            )
        }
}
