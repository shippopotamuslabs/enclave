package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.search.ToolBm25Index

object ToolSearchThresholds {
    const val TAU_BM25 = 1.5
    const val TAU_EMBED = 0.55
    const val DEFAULT_SEARCH_LIMIT = 5
    const val MAX_SEARCH_LIMIT = 10
    const val BM25_K1 = 1.2
    const val BM25_B = 0.75
    const val EMBEDDING_DIMENSION = 384
    const val EMPTY_QUERY_ERROR_MESSAGE = "search_tools requires a non-empty query."
    const val L2_NORM_EPSILON = 1e-6f
    const val BM25_IDF_SMOOTHING = 0.5
    const val EMBEDDING_HASH_MODULUS = 997
    const val MAX_ACTIVATED_TOOLS_PER_SESSION = 15
}

data class ToolSearchDocument(
    val toolName: String,
    val searchableText: String,
)

enum class ToolMatchMethod {
    BM25,
    EMBEDDING,
}

data class ToolSearchMatch(
    val name: String,
    val description: String,
    val score: Double,
    val matchMethod: ToolMatchMethod,
)

data class ToolSearchResult(
    val resultJson: String,
    val activatedToolNames: List<String>,
)

data class ToolSearchIndexSnapshot(
    val documents: List<ToolSearchDocument>,
    val bm25: ToolBm25Index,
    val embeddingsByToolName: Map<String, FloatArray>,
)
