package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.inference.ToolSearchDocument
import com.browntowndev.enclave.domain.model.inference.ToolSearchIndexSnapshot
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import com.browntowndev.enclave.domain.search.ToolBm25Index
import com.browntowndev.enclave.domain.search.ToolCosineSimilarity
import kotlin.math.sqrt

internal object ToolSearchTestFixtures {
    val bm25Catalog: List<ToolCatalogEntry> =
        listOf(
            catalogEntry(
                name = "append_blocks",
                description = "Add content to the in-progress PDF/Word document",
                tags = listOf("artifact"),
            ),
            catalogEntry(
                name = "search_chat_history",
                description = "Search and search through the user's past conversation search history",
                tags = listOf("memory"),
            ),
            catalogEntry(
                name = "read_context_reference",
                description = "Retrieve the full content of a context reference",
                tags = listOf("context"),
            ),
        )

    fun bm25Documents(): List<ToolSearchDocument> =
        bm25Catalog.map { entry ->
            ToolSearchDocument(
                toolName = entry.name,
                searchableText =
                    listOf(entry.name, entry.description, entry.categoryTags.joinToString(" "))
                        .joinToString(" ")
                        .lowercase(),
            )
        }

    fun bm25Index(): ToolBm25Index = ToolBm25Index.build(bm25Documents())

    fun controlledEmbeddingMap(): Map<String, FloatArray> =
        mapOf(
            "append_blocks" to unitVector(dim = 0),
            "search_chat_history" to unitVector(dim = 1),
            "read_context_reference" to unitVector(dim = 2),
        )

    fun searchIndex(embeddings: Map<String, FloatArray> = controlledEmbeddingMap()): ToolSearchIndexSnapshot =
        ToolSearchIndexSnapshot(
            documents = bm25Documents(),
            bm25 = bm25Index(),
            embeddingsByToolName = embeddings,
        )

    fun queryEmbeddingDocumentWriting(): FloatArray = unitVector(dim = 0)

    fun queryEmbeddingUnrelatedQuantum(): FloatArray = unitVector(dim = 3)

    fun genericTestEmbedding(): FloatArray {
        val base = floatArrayOf(0.1f, 0.2f, 0.3f)
        return FloatArray(ToolSearchThresholds.EMBEDDING_DIMENSION) { index ->
            base[index % base.size]
        }
    }

    private fun catalogEntry(
        name: String,
        description: String,
        tags: List<String> = emptyList(),
        disclosureMode: ToolDisclosureMode = ToolDisclosureMode.DISCLOSE_ON_DEMAND,
    ): ToolCatalogEntry =
        ToolCatalogEntry(
            name = name,
            description = description,
            disclosureMode = disclosureMode,
            categoryTags = tags,
        )

    private fun unitVector(
        dim: Int,
        size: Int = ToolSearchThresholds.EMBEDDING_DIMENSION,
    ): FloatArray {
        val vector = FloatArray(size)
        vector[dim.coerceIn(0, size - 1)] = 1.0f
        return ToolCosineSimilarity.l2Normalize(vector)
    }

    fun cosine(
        left: FloatArray,
        right: FloatArray,
    ): Double = ToolCosineSimilarity.cosine(left, right)
}
