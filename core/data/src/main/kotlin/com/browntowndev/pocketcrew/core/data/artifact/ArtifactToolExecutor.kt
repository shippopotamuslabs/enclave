@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.AppendBlocksParams
import com.browntowndev.pocketcrew.domain.model.inference.StartArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentValidationPort
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.browntowndev.pocketcrew.domain.util.ArtifactBlockUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtifactToolExecutor
    @Inject
    constructor(
        private val documentDao: DocumentDao,
        private val embeddingDao: EmbeddingDao,
        private val embeddingEngine: EmbeddingEnginePort,
        private val documentValidation: DocumentValidationPort,
        @ApplicationScope private val externalScope: CoroutineScope,
    ) : ToolExecutorPort {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
            when (request.toolName) {
                ToolDefinition.START_ARTIFACT.name -> {
                    val params = request.parameters as StartArtifactParams
                    startArtifact(params, request.assistantMessageId)
                }
                ToolDefinition.APPEND_BLOCKS.name -> {
                    val params = request.parameters as AppendBlocksParams
                    appendBlocks(params, request.assistantMessageId)
                }
                else -> throw IllegalArgumentException("Unsupported tool: ${request.toolName}")
            }

        private suspend fun startArtifact(
            params: StartArtifactParams,
            assistantMessageId: MessageId?,
        ): ToolExecutionResult {
            val messageId =
                assistantMessageId ?: return ToolExecutionResult(
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    resultJson = """{"status":"error","message":"Missing assistantMessageId for start_artifact"}""",
                )

            val artifactId = UUID.randomUUID().toString()

            // Initial empty document structure
            val initialDocJson =
                buildJsonObject {
                    put("version", JsonPrimitive("1.0"))
                    put(
                        "metadata",
                        buildJsonObject {
                            put("title", JsonPrimitive(params.title))
                            params.author?.let { put("author", JsonPrimitive(it)) }
                        },
                    )
                    put("blocks", JsonArray(emptyList()))
                }

            val entity =
                DocumentEntity(
                    id = artifactId,
                    messageId = messageId,
                    title = params.title,
                    author = params.author,
                    targetFormat = null,
                    json = initialDocJson.toString(),
                    pendingSync = 1,
                )

            documentValidation.validate(entity.json).onFailure { cause ->
                return documentSchemaViolationResult(
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    phase = "initial_empty_document",
                    cause = cause,
                )
            }

            documentDao.insertDocument(entity)

            return ToolExecutionResult(
                toolName = ToolDefinition.START_ARTIFACT.name,
                resultJson = """{"status":"success","artifactId":"$artifactId","message":"Artifact initialized. Use this artifactId to append blocks."}""",
            )
        }

        private suspend fun appendBlocks(
            params: AppendBlocksParams,
            assistantMessageId: MessageId?,
        ): ToolExecutionResult {
            val entity =
                documentDao.getDocumentById(params.artifactId)
                    ?: return ToolExecutionResult(
                        toolName = ToolDefinition.APPEND_BLOCKS.name,
                        resultJson = """{"status":"error","message":"Artifact not found: ${params.artifactId}"}""",
                    )

            val currentDoc: JsonObject = json.parseToJsonElement(entity.json) as JsonObject
            val previousBlocks = (currentDoc["blocks"] as JsonArray)
            val currentBlocks = previousBlocks.toMutableList()
            val startIndex = currentBlocks.size
            currentBlocks.addAll(params.blocks)

            val updatedDoc =
                buildJsonObject {
                    currentDoc.forEach { (key, value) ->
                        if (key == "blocks") {
                            put("blocks", JsonArray(currentBlocks))
                        } else {
                            put(key, value)
                        }
                    }
                }

            val mergedJson = updatedDoc.toString()
            documentValidation.validate(mergedJson).onFailure { cause ->
                return documentSchemaViolationResult(
                    toolName = ToolDefinition.APPEND_BLOCKS.name,
                    phase = "merged_document_after_append",
                    cause = cause,
                )
            }

            val updatedEntity =
                entity.copy(
                    messageId = assistantMessageId ?: entity.messageId,
                    json = mergedJson,
                    updatedAt = System.currentTimeMillis(),
                    pendingSync = 1,
                )

            documentDao.updateDocument(updatedEntity)

            // Real-time embedding (fire and forget on external scope)
            externalScope.launch {
                params.blocks.forEachIndexed { index, blockElement ->
                    val blockText = ArtifactBlockUtil.getVisibleText(blockElement)
                    if (blockText.isBlank()) return@forEachIndexed

                    val blockIndex = startIndex + index
                    val compositeId = "${params.artifactId}_$blockIndex"
                    val hash = blockText.sha256()

                    try {
                        val embedding = embeddingEngine.getEmbedding(blockText)
                        val buffer =
                            java.nio.ByteBuffer
                                .allocate(embedding.size * 4)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        for (f in embedding) buffer.putFloat(f)
                        val vectorBlob = buffer.array()

                        documentDao.insertBlockEmbedding(
                            androidx.sqlite.db.SimpleSQLiteQuery(
                                "INSERT OR REPLACE INTO artifact_block_embeddings(id, vector) VALUES (?, ?)",
                                arrayOf(compositeId, vectorBlob),
                            ),
                        )
                        embeddingDao.insertEmbeddingMetadata(EmbeddingEntity(compositeId, hash))
                    } catch (_: Exception) {
                        // Log error but don't fail tool execution
                    }
                }
            }

            val message =
                if (params.isComplete) {
                    "Document complete. Total blocks: ${currentBlocks.size}. PDF/Word export is now available."
                } else {
                    "Appended ${params.blocks.size} blocks. Total so far: ${currentBlocks.size}. Please continue generating the next section."
                }

            return ToolExecutionResult(
                toolName = ToolDefinition.APPEND_BLOCKS.name,
                resultJson =
                    buildJsonObject {
                        put("status", JsonPrimitive("success"))
                        put("totalBlocks", JsonPrimitive(currentBlocks.size))
                        put("isComplete", JsonPrimitive(params.isComplete))
                        put("message", JsonPrimitive(message))
                        if (params.isComplete) {
                            put("fullDocument", updatedDoc)
                            put("title", JsonPrimitive(entity.title))
                        }
                    }.toString(),
            )
        }

        private fun documentSchemaViolationResult(
            toolName: String,
            phase: String,
            cause: Throwable,
        ): ToolExecutionResult {
            val details = cause.message ?: cause.toString()
            val payload =
                buildJsonObject {
                    put("status", JsonPrimitive("error"))
                    put("code", JsonPrimitive("DOCUMENT_SCHEMA_VIOLATION"))
                    put("phase", JsonPrimitive(phase))
                    put(
                        "message",
                        JsonPrimitive(
                            "The document JSON failed Rust-backed schema validation (Pocket Crew Document schema). " +
                                "Fix the blocks in this tool call and try again.",
                        ),
                    )
                    put("validationDetails", JsonPrimitive(details))
                    put(
                        "schemaHint",
                        JsonPrimitive(
                            "Each block is a tagged union: include \"type\" as one of heading, paragraph, list, " +
                                "table, code, math, image, page_break, with the required fields for that variant " +
                                "(e.g. heading requires integer \"level\" and string \"text\"). " +
                                "The authoritative schema is exported from the same docgen library used for validation.",
                        ),
                    )
                }
            return ToolExecutionResult(toolName = toolName, resultJson = payload.toString())
        }

        private fun String.sha256(): String =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
