package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.LiteRtNativeToolNames
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolMatchMethod
import com.browntowndev.enclave.domain.model.inference.ToolSearchIndexSnapshot
import com.browntowndev.enclave.domain.model.inference.ToolSearchMatch
import com.browntowndev.enclave.domain.model.inference.ToolSearchResult
import com.browntowndev.enclave.domain.model.inference.ToolSearchThresholds
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.search.ToolCosineSimilarity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ToolSearchResolver {
    suspend fun resolve(
        query: String,
        limit: Int,
        toolCatalog: List<ToolCatalogEntry>,
        activeTools: List<ToolDefinition>,
        searchIndex: ToolSearchIndexSnapshot,
        embeddingEngine: EmbeddingEnginePort,
        strategy: ToolCallStrategy,
    ): ToolSearchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return ToolSearchResult(
                resultJson = emptyQueryPayload(trimmedQuery),
                activatedToolNames = emptyList(),
            )
        }

        val effectiveLimit = limit.coerceIn(1, ToolSearchThresholds.MAX_SEARCH_LIMIT)
        val descriptionsByName = toolCatalog.associate { it.name to it.description }
        val activeNames = activeTools.map { it.name }.toSet()
        val catalogByName = toolCatalog.associateBy { it.name }

        var matches =
            searchIndex.bm25
                .search(
                    query = trimmedQuery,
                    limit = searchIndex.documents.size.coerceAtLeast(effectiveLimit),
                    descriptionsByName = descriptionsByName,
                ).filter { match -> match.score > 0.0 }
                .take(effectiveLimit)

        matches = filterMatchesForStrategy(matches, strategy)

        val topBm25Score = matches.firstOrNull()?.score ?: 0.0
        if (matches.isEmpty() || topBm25Score < ToolSearchThresholds.TAU_BM25) {
            val queryEmbedding = embeddingEngine.getEmbedding(trimmedQuery)
            matches =
                embeddingSearch(
                    queryEmbedding = queryEmbedding,
                    searchIndex = searchIndex,
                    descriptionsByName = descriptionsByName,
                    limit = effectiveLimit,
                )
            matches = filterMatchesForStrategy(matches, strategy)
        }

        val activated = mutableListOf<String>()
        val alreadyActive = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        matches.forEach { match ->
            if (!seen.add(match.name)) return@forEach
            if (match.name in activeNames) {
                alreadyActive += match.name
            } else if (catalogByName.containsKey(match.name)) {
                activated += match.name
            }
        }

        val resultJson =
            buildJsonObject {
                put("status", "ok")
                put("query", trimmedQuery)
                put("matches", matches.toJsonArray())
                put("activated_tools", activated.toJsonStringArray())
                put("already_active_tools", alreadyActive.toJsonStringArray())
                put(
                    "tools",
                    buildToolsArray(
                        activated = activated,
                        catalogByName = catalogByName,
                        strategy = strategy,
                    ),
                )
            }.toString()

        return ToolSearchResult(
            resultJson = resultJson,
            activatedToolNames = activated,
        )
    }

    private fun filterMatchesForStrategy(
        matches: List<ToolSearchMatch>,
        strategy: ToolCallStrategy,
    ): List<ToolSearchMatch> =
        if (strategy == ToolCallStrategy.LITE_RT_NATIVE) {
            matches.filter { match -> LiteRtNativeToolNames.isCallable(match.name) }
        } else {
            matches
        }

    private suspend fun embeddingSearch(
        queryEmbedding: FloatArray,
        searchIndex: ToolSearchIndexSnapshot,
        descriptionsByName: Map<String, String>,
        limit: Int,
    ): List<ToolSearchMatch> {
        val normalizedQuery = ToolCosineSimilarity.l2Normalize(queryEmbedding)
        return searchIndex.embeddingsByToolName
            .map { (toolName, embedding) ->
                val score = ToolCosineSimilarity.cosine(normalizedQuery, embedding)
                ToolSearchMatch(
                    name = toolName,
                    description = descriptionsByName[toolName].orEmpty(),
                    score = score,
                    matchMethod = ToolMatchMethod.EMBEDDING,
                )
            }.filter { match -> match.score >= ToolSearchThresholds.TAU_EMBED }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun buildToolsArray(
        activated: List<String>,
        catalogByName: Map<String, ToolCatalogEntry>,
        strategy: ToolCallStrategy,
    ): JsonArray =
        buildJsonArray {
            activated.forEach { name ->
                val entry = catalogByName.getValue(name)
                val def = ToolDefinition.fromName(name)
                add(
                    buildJsonObject {
                        put("name", entry.name)
                        put("description", entry.description)
                        put(
                            "parameters",
                            def?.schema?.toJsonObject() ?: emptyJsonObject(),
                        )
                        if (def != null) {
                            put("call_example", def.toExample(strategy))
                        }
                    },
                )
            }
        }

    private fun List<ToolSearchMatch>.toJsonArray(): JsonArray =
        buildJsonArray {
            forEach { match ->
                add(
                    buildJsonObject {
                        put("name", match.name)
                        put("description", match.description)
                        put("score", match.score)
                        put("match_method", match.matchMethod.name.lowercase())
                    },
                )
            }
        }

    private fun emptyQueryPayload(query: String): String =
        buildJsonObject {
            put("status", "error")
            put("message", ToolSearchThresholds.EMPTY_QUERY_ERROR_MESSAGE)
            put("query", query)
            put("matches", buildJsonArray {})
            put("activated_tools", buildJsonArray {})
            put("already_active_tools", buildJsonArray {})
            put("tools", buildJsonArray {})
        }.toString()

    private fun List<String>.toJsonStringArray(): JsonArray = buildJsonArray { forEach { add(JsonPrimitive(it)) } }

    private fun emptyJsonObject(): JsonElement = JsonObject(emptyMap())

}
