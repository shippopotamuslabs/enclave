package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort

/**
 * [EmbeddingEnginePort] fake (ENC-28). Returns a deterministic pseudo-embedding
 * derived from the text so equal texts embed equally; the actual values carry no
 * semantics. Records every queried text.
 */
class FakeEmbeddingEngine(
    private val dimensions: Int = 8,
) : EmbeddingEnginePort {
    val queries = mutableListOf<String>()

    /** Optional per-text scripted embeddings; a text present here wins over the derived vector. */
    val embeddingsByText = mutableMapOf<String, FloatArray>()

    /** When set, [getEmbedding] returns this vector for every text without a per-text entry (ENC-28). */
    var uniformEmbedding: FloatArray? = null

    /** When set, [getEmbedding] throws it after recording the query (ENC-28). */
    var failure: Throwable? = null

    override suspend fun getEmbedding(text: String): FloatArray {
        queries += text
        failure?.let { throw it }
        embeddingsByText[text]?.let { return it }
        uniformEmbedding?.let { return it }
        val seed = text.hashCode()
        return FloatArray(dimensions) { i -> ((seed shr (i % 16)) and BYTE_MASK) / BYTE_RANGE }
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val BYTE_RANGE = 255f
    }

    override suspend fun close() = Unit
}
