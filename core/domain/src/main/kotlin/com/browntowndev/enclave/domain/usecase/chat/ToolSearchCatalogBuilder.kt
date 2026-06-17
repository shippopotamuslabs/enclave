package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolSearchIndexSnapshot
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.NoopToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.search.ToolBm25Index
import com.browntowndev.enclave.domain.search.toSearchDocument

object ToolSearchCatalogBuilder {
    /** BM25-only snapshot; embedding fallback is a no-op until [build] precomputes vectors. */
    fun buildBm25Only(catalog: List<ToolCatalogEntry>): ToolSearchIndexSnapshot {
        val documents = catalog.map { entry -> entry.toSearchDocument() }
        return ToolSearchIndexSnapshot(
            documents = documents,
            bm25 = ToolBm25Index.build(documents),
            embeddingsByToolName = emptyMap(),
        )
    }

    suspend fun build(
        catalog: List<ToolCatalogEntry>,
        embeddingEngine: EmbeddingEnginePort,
        embeddingCache: ToolSearchEmbeddingCachePort = NoopToolSearchEmbeddingCachePort,
    ): ToolSearchIndexSnapshot {
        embeddingCache.ensureStaticToolsWarmed(embeddingEngine)
        val documents = catalog.map { entry -> entry.toSearchDocument() }
        val embeddingsByToolName =
            buildMap {
                documents.forEach { document ->
                    val embedding =
                        embeddingCache.normalizedEmbeddingFor(
                            toolName = document.toolName,
                            searchableText = document.searchableText,
                            embeddingEngine = embeddingEngine,
                        )
                    put(document.toolName, embedding.copyOf())
                }
            }
        return ToolSearchIndexSnapshot(
            documents = documents,
            bm25 = ToolBm25Index.build(documents),
            embeddingsByToolName = embeddingsByToolName,
        )
    }
}
