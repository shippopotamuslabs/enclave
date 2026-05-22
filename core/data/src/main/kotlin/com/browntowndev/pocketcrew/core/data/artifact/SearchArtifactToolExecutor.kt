package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.security.AadContext
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.model.inference.SearchArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import androidx.sqlite.db.SimpleSQLiteQuery
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchArtifactToolExecutor
    @Inject
    constructor(
        private val documentDao: DocumentDao,
        private val embeddingEngine: EmbeddingEnginePort,
        private val localEncryption: LocalDataEncryptionService,
    ) : ToolExecutorPort {
        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            val params = request.parameters as SearchArtifactParams

            if (!isSafeArtifactId(params.artifactId)) {
                return ToolExecutionResult(
                    toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                    resultJson = """{"status":"error","message":"Invalid artifactId"}""",
                )
            }

            val queryEmbedding = embeddingEngine.getEmbedding(params.query)
            val queryBlob = floatArrayToByteArray(queryEmbedding)
            val idPrefix = "${params.artifactId}_%"

            val sql =
                """
                SELECT id FROM artifact_block_embeddings
                WHERE id LIKE ? AND vector MATCH ? AND k = 5
                ORDER BY distance
                """.trimIndent()

            val matchingIds =
                documentDao.searchSimilarBlocks(
                    SimpleSQLiteQuery(sql, arrayOf(idPrefix, queryBlob)),
                )

            if (matchingIds.isEmpty()) {
                return ToolExecutionResult(
                    toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                    resultJson = """{"status":"success","results":[],"message":"No relevant blocks found for query: ${params.query}"}""",
                )
            }

            val entity =
                documentDao.getDocumentById(params.artifactId)
                    ?: return ToolExecutionResult(
                        toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                        resultJson = """{"status":"error","message":"Artifact not found: ${params.artifactId}"}""",
                    )

            val docId = params.artifactId
            val doc: JsonObject =
                json.parseToJsonElement(
                    localEncryption.decryptString(
                        entity.json,
                        AadContext("DocumentEntity", "json", docId).toBytes(),
                    ),
                ) as JsonObject
            val blocks = doc["blocks"] as JsonArray

            val results =
                matchingIds.mapNotNull { id ->
                    try {
                        val blockIndex = id.substringAfterLast("_").toInt()
                        if (blockIndex < blocks.size) {
                            val block = blocks[blockIndex]
                            buildJsonObject {
                                put("blockIndex", JsonPrimitive(blockIndex))
                                put("content", block)
                            }
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

            return ToolExecutionResult(
                toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                resultJson =
                    buildJsonObject {
                        put("status", JsonPrimitive("success"))
                        put("results", JsonArray(results))
                        put("message", JsonPrimitive("Found ${results.size} relevant blocks."))
                    }.toString(),
            )
        }

        private fun isSafeArtifactId(artifactId: String): Boolean =
            artifactId.isNotEmpty() &&
                artifactId.length <= MAX_ARTIFACT_ID_LENGTH &&
                SAFE_ARTIFACT_ID_PATTERN.matches(artifactId)

        private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(floatArray.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in floatArray) buffer.putFloat(f)
            return buffer.array()
        }

        private companion object {
            private const val MAX_ARTIFACT_ID_LENGTH = 128
            private val SAFE_ARTIFACT_ID_PATTERN = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]*$""")
        }
    }
