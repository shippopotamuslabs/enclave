package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaGenerationRequestMapper
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MediaToolExecutor
    @Inject
    constructor(
        private val generateMediaUseCase: GenerateMediaUseCase,
        private val loggingPort: LoggingPort,
    ) : ToolExecutorPort {
        open override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
            when (request.toolName) {
                ToolDefinition.GENERATE_IMAGE.name,
                ToolDefinition.GENERATE_VIDEO.name,
                ToolDefinition.GENERATE_MUSIC.name,
                -> {
                    try {
                        val mediaRequest = MediaGenerationRequestMapper.fromToolCall(request)
                        loggingPort.debug(TAG, "Executing ${request.toolName}: prompt='${mediaRequest.prompt}'")
                        generateMediaUseCase(mediaRequest).fold(
                            onSuccess = { assets ->
                                ToolExecutionResult(
                                    toolName = request.toolName,
                                    resultJson = buildResultJson(assets),
                                )
                            },
                            onFailure = { error ->
                                val message =
                                    if (error is java.io.IOException) {
                                        "Network error: ${error.message}"
                                    } else {
                                        error.message ?: "Unknown error during media generation"
                                    }
                                ToolExecutionResult(
                                    toolName = request.toolName,
                                    resultJson = buildErrorJson(message),
                                )
                            },
                        )
                    } catch (e: java.io.IOException) {
                        loggingPort.warning(TAG, "Media generation failed with network error: ${e.message}")
                        ToolExecutionResult(
                            toolName = request.toolName,
                            resultJson = buildErrorJson("Network error: ${e.message}"),
                        )
                    }
                }
                else -> throw IllegalArgumentException("Unsupported media tool: ${request.toolName}")
            }

        private fun buildResultJson(assets: List<GeneratedMediaAsset>): String =
            buildResultJson(
                uris = assets.map { it.localUri },
                ids = assets.map { it.id },
                types = assets.map { it.mediaType },
                prompts = assets.map { it.prompt },
            )

        private fun buildResultJson(
            uris: List<String>,
            ids: List<String>,
            types: List<String>,
            prompts: List<String>,
        ): String =
            buildJsonObject {
                put("success", true)
                put("count", uris.size)
                putJsonArray("asset_uris") {
                    uris.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                putJsonArray("asset_ids") {
                    ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                putJsonArray("asset_types") {
                    types.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                putJsonArray("asset_prompts") {
                    prompts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                put("message", "Media generated successfully and available in Studio.")
            }.toString()

        private fun buildErrorJson(error: String): String =
            buildJsonObject {
                put("success", false)
                put("error", error)
            }.toString()

        private companion object {
            private const val TAG = "MediaToolExecutor"
        }
    }
