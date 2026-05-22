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

package com.browntowndev.enclave.core.data.artifact

import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EmbeddingEntity
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.AppendBlocksParams
import com.browntowndev.enclave.domain.model.inference.StartArtifactParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.docgen.DocumentValidationPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.domain.util.ArtifactBlockUtil
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
        private val localEncryption: LocalDataEncryptionService,
        @ApplicationScope private val externalScope: CoroutineScope,
        private val newArtifactDocumentId: () -> String = { UUID.randomUUID().toString() },
    ) : ToolExecutorPort {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
            when (request.toolName) {
                ToolDefinition.START_ARTIFACT.name -> {
                    documentValidation.validateStartArtifactArgs(request.argumentsJson).onFailure { cause ->
                        return toolArgumentsViolationResult(
                            toolName = ToolDefinition.START_ARTIFACT.name,
                            phase = "start_artifact_arguments",
                            cause = cause,
                        )
                    }
                    val params = request.parameters as StartArtifactParams
                    startArtifact(params, request.assistantMessageId)
                }
                ToolDefinition.APPEND_BLOCKS.name -> {
                    documentValidation.validateAppendBlocksArgs(request.argumentsJson).onFailure { cause ->
                        return toolArgumentsViolationResult(
                            toolName = ToolDefinition.APPEND_BLOCKS.name,
                            phase = "append_blocks_arguments",
                            cause = cause,
                        )
                    }
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

            val artifactId = newArtifactDocumentId()

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

            val rawJson = initialDocJson.toString()
            documentValidation.validate(rawJson).onFailure { cause ->
                return documentSchemaViolationResult(
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    phase = "initial_empty_document",
                    cause = cause,
                )
            }

            val entity =
                DocumentEntity(
                    id = artifactId,
                    messageId = messageId,
                    title =
                        localEncryption.encryptString(
                            params.title,
                            AadContext("DocumentEntity", "title", artifactId).toBytes(),
                        ),
                    author =
                        localEncryption.encryptNullable(
                            params.author,
                            AadContext("DocumentEntity", "author", artifactId).toBytes(),
                        ),
                    targetFormat = null,
                    json =
                        localEncryption.encryptString(
                            rawJson,
                            AadContext("DocumentEntity", "json", artifactId).toBytes(),
                        ),
                    pendingSync = 0,
                )

            documentDao.insertDocument(entity)

            return ToolExecutionResult(
                toolName = ToolDefinition.START_ARTIFACT.name,
                resultJson =
                    buildJsonObject {
                        put("status", JsonPrimitive("success"))
                        put("artifactId", JsonPrimitive(artifactId))
                        put(
                            "message",
                            JsonPrimitive(
                                "Artifact initialized. artifactId is \"$artifactId\". " +
                                    "Call append_blocks with this exact artifactId. " +
                                    "On your LAST append_blocks call you MUST set isComplete:true " +
                                    "or the document will not be delivered to the user.",
                            ),
                        )
                    }.toString(),
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

            val docId = params.artifactId
            val jsonAad = AadContext("DocumentEntity", "json", docId).toBytes()
            val currentDoc: JsonObject =
                json.parseToJsonElement(localEncryption.decryptString(entity.json, jsonAad)) as JsonObject
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
                    json = localEncryption.encryptString(mergedJson, jsonAad),
                    updatedAt = System.currentTimeMillis(),
                    pendingSync = if (params.isComplete) 1 else 0,
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

                        documentDao.deleteBlockEmbedding(
                            androidx.sqlite.db.SimpleSQLiteQuery(
                                "DELETE FROM artifact_block_embeddings WHERE id = ?",
                                arrayOf(compositeId),
                            ),
                        )
                        documentDao.insertBlockEmbedding(
                            androidx.sqlite.db.SimpleSQLiteQuery(
                                "INSERT INTO artifact_block_embeddings(id, vector) VALUES (?, ?)",
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
                    "Appended ${params.blocks.size} blocks. Total so far: ${currentBlocks.size}. " +
                        "Continue appending remaining sections. " +
                        "When the document is fully written, include the final blocks AND set isComplete:true " +
                        "in the same call — do not make a separate empty-blocks finalization call."
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
                            put(
                                "title",
                                JsonPrimitive(
                                    localEncryption.decryptString(
                                        entity.title,
                                        AadContext("DocumentEntity", "title", docId).toBytes(),
                                    ),
                                ),
                            )
                        }
                    }.toString(),
            )
        }

        private fun toolArgumentsViolationResult(
            toolName: String,
            phase: String,
            cause: Throwable,
        ): ToolExecutionResult {
            val details = cause.message ?: cause.toString()
            val payload =
                buildJsonObject {
                    put("status", JsonPrimitive("error"))
                    put("code", JsonPrimitive("TOOL_ARGUMENTS_VIOLATION"))
                    put("phase", JsonPrimitive(phase))
                    put(
                        "message",
                        JsonPrimitive(
                            "Tool arguments failed Rust-backed validation. Fix the JSON and try again.",
                        ),
                    )
                    put("validationDetails", JsonPrimitive(details))
                    put(
                        "schemaHint",
                        JsonPrimitive(
                            "start_artifact: {\"title\":\"string\",\"author?\":\"string\"} (camelCase). " +
                                "append_blocks: {\"artifactId\":\"uuid-from-start_artifact\",\"blocks\":[block...]," +
                                "\"isComplete\":boolean}. " +
                                "Each block needs \"type\": heading|paragraph|list|table|code|math|image|page_break " +
                                "with the required fields for that variant.",
                        ),
                    )
                }
            return ToolExecutionResult(toolName = toolName, resultJson = payload.toString())
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
                            "The document JSON failed Rust-backed schema validation (Enclave Document schema). " +
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
