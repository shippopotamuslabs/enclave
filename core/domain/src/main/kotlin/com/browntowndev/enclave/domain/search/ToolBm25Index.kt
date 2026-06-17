package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.domain.model.inference.ToolMatchMethod
import com.browntowndev.enclave.domain.model.inference.ToolSearchDocument
import com.browntowndev.enclave.domain.model.inference.ToolSearchMatch
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import kotlin.math.ln

/**
 * Pure Kotlin BM25 index over tokenized [ToolSearchDocument] rows.
 */
class ToolBm25Index private constructor(
    private val documents: List<ToolSearchDocument>,
    private val tokenizedDocs: List<List<String>>,
    private val termFreqs: List<Map<String, Int>>,
    private val documentFrequencies: Map<String, Int>,
    private val k1: Double,
    private val b: Double,
    private val avgDocLength: Double,
    private val docCount: Int,
) {
    fun search(
        query: String,
        limit: Int,
        descriptionsByName: Map<String, String> = emptyMap(),
    ): List<ToolSearchMatch> {
        if (documents.isEmpty() || limit <= 0) return emptyList()
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scores =
            documents.indices.mapNotNull { docIndex ->
                val score = scoreDocument(queryTokens, docIndex)
                if (score <= 0.0) {
                    null
                } else {
                    val doc = documents[docIndex]
                    ToolSearchMatch(
                        name = doc.toolName,
                        description = descriptionsByName[doc.toolName].orEmpty(),
                        score = score,
                        matchMethod = ToolMatchMethod.BM25,
                    )
                }
            }

        return scores
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun scoreDocument(
        queryTokens: List<String>,
        docIndex: Int,
    ): Double {
        val docTokens = tokenizedDocs[docIndex]
        if (docTokens.isEmpty()) return 0.0
        val docLength = docTokens.size.toDouble()
        val termFreq = termFreqs[docIndex]

        return queryTokens.distinct().sumOf { term ->
            val tf = termFreq[term] ?: 0
            if (tf == 0) {
                0.0
            } else {
                val df = documentFrequencies[term] ?: 0
                val smoothing = ToolSearchThresholds.BM25_IDF_SMOOTHING
                val idf = ln((docCount - df + smoothing) / (df + smoothing) + 1.0)
                val numerator = tf * (k1 + 1.0)
                val denominator = tf + k1 * (1.0 - b + b * (docLength / avgDocLength))
                idf * (numerator / denominator)
            }
        }
    }

    companion object {
        private val TOKEN_REGEX = Regex("[^a-z0-9]+")

        fun tokenize(text: String): List<String> =
            text
                .lowercase()
                .split(TOKEN_REGEX)
                .filter { it.isNotEmpty() }

        fun build(
            documents: List<ToolSearchDocument>,
            k1: Double = ToolSearchThresholds.BM25_K1,
            b: Double = ToolSearchThresholds.BM25_B,
        ): ToolBm25Index {
            val tokenized = documents.map { document -> tokenize(document.searchableText) }
            val avgLength =
                if (tokenized.isEmpty()) {
                    0.0
                } else {
                    tokenized.sumOf { it.size }.toDouble() / tokenized.size.toDouble()
                }
            val termFreqs = tokenized.map { docTokens -> docTokens.groupingBy { it }.eachCount() }
            val documentFrequencies = mutableMapOf<String, Int>()
            tokenized.forEach { docTokens ->
                docTokens.distinct().forEach { term ->
                    documentFrequencies[term] = (documentFrequencies[term] ?: 0) + 1
                }
            }
            return ToolBm25Index(
                documents = documents,
                tokenizedDocs = tokenized,
                termFreqs = termFreqs,
                documentFrequencies = documentFrequencies,
                k1 = k1,
                b = b,
                avgDocLength = avgLength.coerceAtLeast(1.0),
                docCount = documents.size.coerceAtLeast(1),
            )
        }
    }
}
