package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds

/**
 * Fallback embedding port for hosts that only need BM25 search (e.g. tests or BM25-only index builds).
 */
object Bm25OnlyEmbeddingEnginePort : EmbeddingEnginePort {
    override suspend fun getEmbedding(text: String): FloatArray =
        FloatArray(ToolSearchThresholds.EMBEDDING_DIMENSION) { index ->
            ((text.hashCode() + index) % ToolSearchThresholds.EMBEDDING_HASH_MODULUS).toFloat() /
                ToolSearchThresholds.EMBEDDING_HASH_MODULUS.toFloat()
        }

    override suspend fun close() = Unit
}
