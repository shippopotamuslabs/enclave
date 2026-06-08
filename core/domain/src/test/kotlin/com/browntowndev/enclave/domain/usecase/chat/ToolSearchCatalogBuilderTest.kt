package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolSearchCatalogBuilderTest {
    @Test
    fun `TSD-16 catalog builder embeds every catalog entry once`() =
        runTest {
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns ToolSearchTestFixtures.genericTestEmbedding()

            val snapshot =
                ToolSearchCatalogBuilder.build(
                    catalog = ToolSearchTestFixtures.bm25Catalog,
                    embeddingEngine = embeddingEngine,
                )

            assertEquals(3, snapshot.embeddingsByToolName.size)
            assertEquals(
                ToolSearchThresholds.EMBEDDING_DIMENSION,
                snapshot.embeddingsByToolName.getValue("append_blocks").size,
            )
            coVerify(exactly = 3) { embeddingEngine.getEmbedding(any()) }
        }
}
