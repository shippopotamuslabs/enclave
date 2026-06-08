package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.usecase.chat.ToolSearchCatalogBuilder
import com.browntowndev.enclave.domain.usecase.chat.ToolSearchTestFixtures
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryToolSearchEmbeddingCacheTest {
    @Test
    fun `ensureStaticToolsWarmed embeds static catalog only once`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns ToolSearchTestFixtures.genericTestEmbedding()
            val cache = InMemoryToolSearchEmbeddingCache()

            cache.ensureStaticToolsWarmed(embeddingEngine)
            cache.ensureStaticToolsWarmed(embeddingEngine)

            coVerify(exactly = ToolDefinition.ALL_TOOLS.size) { embeddingEngine.getEmbedding(any()) }
        }

    @Test
    fun `catalog builder second build reuses cache without additional embeddings`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns ToolSearchTestFixtures.genericTestEmbedding()
            val cache = InMemoryToolSearchEmbeddingCache()

            ToolSearchCatalogBuilder.build(
                catalog = ToolSearchTestFixtures.bm25Catalog,
                embeddingEngine = embeddingEngine,
                embeddingCache = cache,
            )
            clearMocks(embeddingEngine, answers = false)
            coEvery { embeddingEngine.getEmbedding(any()) } returns ToolSearchTestFixtures.genericTestEmbedding()

            ToolSearchCatalogBuilder.build(
                catalog = ToolSearchTestFixtures.bm25Catalog,
                embeddingEngine = embeddingEngine,
                embeddingCache = cache,
            )

            coVerify(exactly = 0) { embeddingEngine.getEmbedding(any()) }
        }

    @Test
    fun `ensureStaticToolsWarmed deduplicates concurrent startup calls`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns ToolSearchTestFixtures.genericTestEmbedding()
            val cache = InMemoryToolSearchEmbeddingCache()

            coroutineScope {
                List(8) {
                    async { cache.ensureStaticToolsWarmed(embeddingEngine) }
                }.awaitAll()
            }

            coVerify(exactly = ToolDefinition.ALL_TOOLS.size) { embeddingEngine.getEmbedding(any()) }
        }

    @Test
    fun `normalizedEmbeddingFor returns stable vectors for same tool`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("alpha tool") } returns floatArrayOf(3f, 4f)
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
            coVerify(exactly = 1) { embeddingEngine.getEmbedding("alpha tool") }
        }

    @Test
    fun `normalizedEmbeddingFor recomputes when searchable text changes`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding("alpha v1") } returns floatArrayOf(3f, 4f)
            coEvery { embeddingEngine.getEmbedding("alpha v2") } returns floatArrayOf(0f, 5f)
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

            coVerify(exactly = 1) { embeddingEngine.getEmbedding("alpha v1") }
            coVerify(exactly = 1) { embeddingEngine.getEmbedding("alpha v2") }
        }
}
