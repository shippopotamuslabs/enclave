package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.usecase.chat.ToolSearchCatalogBuilder
import com.browntowndev.enclave.domain.usecase.chat.ToolSearchTestFixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryToolSearchEmbeddingCacheTest {
    @Test
    fun `ensureStaticToolsWarmed embeds static catalog only once`() =
        runTest {
            val embeddingEngine = FakeEmbeddingEngine()
            val cache = InMemoryToolSearchEmbeddingCache()

            cache.ensureStaticToolsWarmed(embeddingEngine)
            cache.ensureStaticToolsWarmed(embeddingEngine)

            assertEquals(ToolDefinition.ALL_TOOLS.size, embeddingEngine.queries.size)
        }

    @Test
    fun `catalog builder second build reuses cache without additional embeddings`() =
        runTest {
            val embeddingEngine = FakeEmbeddingEngine()
            val cache = InMemoryToolSearchEmbeddingCache()

            ToolSearchCatalogBuilder.build(
                catalog = ToolSearchTestFixtures.bm25Catalog,
                embeddingEngine = embeddingEngine,
                embeddingCache = cache,
            )
            embeddingEngine.queries.clear()

            ToolSearchCatalogBuilder.build(
                catalog = ToolSearchTestFixtures.bm25Catalog,
                embeddingEngine = embeddingEngine,
                embeddingCache = cache,
            )

            assertTrue(embeddingEngine.queries.isEmpty())
        }

    @Test
    fun `ensureStaticToolsWarmed deduplicates concurrent startup calls`() =
        runTest {
            val embeddingEngine = FakeEmbeddingEngine()
            val cache = InMemoryToolSearchEmbeddingCache()

            coroutineScope {
                List(8) {
                    async { cache.ensureStaticToolsWarmed(embeddingEngine) }
                }.awaitAll()
            }

            assertEquals(ToolDefinition.ALL_TOOLS.size, embeddingEngine.queries.size)
        }

    @Test
    fun `normalizedEmbeddingFor returns stable vectors for same tool`() =
        runTest {
            val embeddingEngine =
                FakeEmbeddingEngine().apply {
                    embeddingsByText["alpha tool"] = floatArrayOf(3f, 4f)
                }
            val cache = InMemoryToolSearchEmbeddingCache()

            val first =
                cache.normalizedEmbeddingFor(
                    toolName = "alpha",
                    searchableText = "alpha tool",
                    embeddingEngine = embeddingEngine,
                )
            val second =
                cache.normalizedEmbeddingFor(
                    toolName = "alpha",
                    searchableText = "alpha tool",
                    embeddingEngine = embeddingEngine,
                )

            assertEquals(first.toList(), second.toList())
            assertEquals(1, embeddingEngine.queries.count { it == "alpha tool" })
        }

    @Test
    fun `normalizedEmbeddingFor recomputes when searchable text changes`() =
        runTest {
            val embeddingEngine =
                FakeEmbeddingEngine().apply {
                    embeddingsByText["alpha v1"] = floatArrayOf(3f, 4f)
                    embeddingsByText["alpha v2"] = floatArrayOf(0f, 5f)
                }
            val cache = InMemoryToolSearchEmbeddingCache()

            cache.normalizedEmbeddingFor(
                toolName = "alpha",
                searchableText = "alpha v1",
                embeddingEngine = embeddingEngine,
            )
            cache.normalizedEmbeddingFor(
                toolName = "alpha",
                searchableText = "alpha v2",
                embeddingEngine = embeddingEngine,
            )

            assertEquals(1, embeddingEngine.queries.count { it == "alpha v1" })
            assertEquals(1, embeddingEngine.queries.count { it == "alpha v2" })
        }
}
