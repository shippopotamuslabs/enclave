package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class InMemoryToolSearchEmbeddingCache
    @Inject
    constructor() : ToolSearchEmbeddingCachePort {
        private data class CachedEmbedding(
            val searchableTextHash: Int,
            val vector: FloatArray,
        )

        private val embeddingsByToolName = ConcurrentHashMap<String, CachedEmbedding>()
        private val mutex = Mutex()
        @Volatile
        private var staticToolsWarmed = false

        override suspend fun ensureStaticToolsWarmed(embeddingEngine: EmbeddingEnginePort) {
            if (staticToolsWarmed) return
            mutex.withLock {
                if (staticToolsWarmed) return
                val pendingDocuments =
                    ToolDefinition.ALL_TOOLS
                        .map { tool -> tool.toSearchDocument() }
                        .filter { document ->
                            val cached = embeddingsByToolName[document.toolName]
                            cached == null || cached.searchableTextHash != document.searchableText.hashCode()
                        }
                pendingDocuments.forEach { document ->
                    storeEmbedding(
                        toolName = document.toolName,
                        searchableText = document.searchableText,
                        embeddingEngine = embeddingEngine,
                    )
                }
                staticToolsWarmed = true
            }
        }

        override suspend fun normalizedEmbeddingFor(
            toolName: String,
            searchableText: String,
            embeddingEngine: EmbeddingEnginePort,
        ): FloatArray {
            val textHash = searchableText.hashCode()
            embeddingsByToolName[toolName]?.let { cached ->
                if (cached.searchableTextHash == textHash) {
                    return cached.vector.copyOf()
                }
            }
            val normalized = storeEmbedding(toolName, searchableText, embeddingEngine)
            return normalized.copyOf()
        }

        private suspend fun storeEmbedding(
            toolName: String,
            searchableText: String,
            embeddingEngine: EmbeddingEnginePort,
        ): FloatArray {
            val textHash = searchableText.hashCode()
            val normalized = ToolCosineSimilarity.l2Normalize(embeddingEngine.getEmbedding(searchableText))
            val stored = CachedEmbedding(searchableTextHash = textHash, vector = normalized.copyOf())
            embeddingsByToolName[toolName] = stored
            return stored.vector
        }
    }
