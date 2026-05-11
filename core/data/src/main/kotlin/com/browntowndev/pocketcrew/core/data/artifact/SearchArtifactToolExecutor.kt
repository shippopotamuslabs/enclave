package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.domain.model.inference.SearchArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchArtifactToolExecutor @Inject constructor(
    private val documentDao: DocumentDao,
    private val embeddingEngine: EmbeddingEnginePort
) : ToolExecutorPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        val params = request.parameters as SearchArtifactParams
        
        val queryEmbedding = embeddingEngine.getEmbedding(params.query)
        val vectorString = queryEmbedding.joinToString(",", prefix = "[", postfix = "]")
        
        // Search top 5 similar blocks
        val sql = """
            SELECT id FROM artifact_block_embeddings 
            WHERE id LIKE '${params.artifactId}_%' AND vector MATCH '$vectorString' AND k = 5
            ORDER BY distance
        """.trimIndent()

        val matchingIds = documentDao.searchSimilarBlocks(androidx.sqlite.db.SimpleSQLiteQuery(sql))
        
        if (matchingIds.isEmpty()) {
            return ToolExecutionResult(
                toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                resultJson = """{"status":"success","results":[],"message":"No relevant blocks found for query: ${params.query}"}"""
            )
        }

        val entity = documentDao.getDocumentById(params.artifactId)
            ?: return ToolExecutionResult(
                toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                resultJson = """{"status":"error","message":"Artifact not found: ${params.artifactId}"}"""
            )

        val doc: JsonObject = json.parseToJsonElement(entity.json) as JsonObject
        val blocks = doc["blocks"] as JsonArray
        
        val results = matchingIds.mapNotNull { id ->
            try {
                val blockIndex = id.substringAfterLast("_").toInt()
                if (blockIndex < blocks.size) {
                    val block = blocks[blockIndex]
                    buildJsonObject {
                        put("blockIndex", JsonPrimitive(blockIndex))
                        put("content", block)
                    }
                } else null
            } catch (_: Exception) { null }
        }

        return ToolExecutionResult(
            toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
            resultJson = buildJsonObject {
                put("status", JsonPrimitive("success"))
                put("results", JsonArray(results))
                put("message", JsonPrimitive("Found ${results.size} relevant blocks."))
            }.toString()
        )
    }
}
