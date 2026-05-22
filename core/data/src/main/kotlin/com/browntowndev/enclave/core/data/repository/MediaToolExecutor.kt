package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.GenerateImageToolParams
import com.browntowndev.enclave.domain.model.inference.GenerateMusicToolParams
import com.browntowndev.enclave.domain.model.inference.GenerateVideoToolParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateVideoUseCase
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaToolExecutor
    @Inject
    constructor(
        private val generateImageUseCase: GenerateImageUseCase,
        private val generateVideoUseCase: GenerateVideoUseCase,
        private val generateMusicUseCase: GenerateMusicUseCase,
        private val studioRepository: StudioRepositoryPort,
        private val loggingPort: LoggingPort,
    ) : ToolExecutorPort {
        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
            when (request.toolName) {
                ToolDefinition.GENERATE_IMAGE.name -> executeImage(request)
                ToolDefinition.GENERATE_VIDEO.name -> executeVideo(request)
                ToolDefinition.GENERATE_MUSIC.name -> executeMusic(request)
                else -> throw IllegalArgumentException("Unsupported media tool: ${request.toolName}")
            }

        private suspend fun executeImage(request: ToolCallRequest): ToolExecutionResult {
            val params = request.parameters as GenerateImageToolParams
            val settings =
                ImageGenerationSettings(
                    aspectRatio = AspectRatio.entries.find { it.ratio == params.aspect_ratio } ?: AspectRatio.ONE_ONE,
                    quality =
                        GenerationQuality.entries.find { it.name.equals(params.quality, ignoreCase = true) } ?: GenerationQuality.SPEED,
                    generationCount = params.count.coerceIn(1, 4),
                )

            loggingPort.debug(TAG, "Executing generate_image: prompt='${params.prompt}', settings=$settings")

            return generateImageUseCase(params.prompt, settings).fold(
                onSuccess = { imageBytesList ->
                    val assetUris = mutableListOf<String>()
                    val assetIds = mutableListOf<String>()
                    val assetTypes = mutableListOf<String>()
                    val assetPrompts = mutableListOf<String>()

                    imageBytesList.forEach { bytes ->
                        val uri = studioRepository.cacheEphemeralMedia(bytes, "IMAGE")
                        val asset =
                            com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                                id = "chat_media_${java.util.UUID.randomUUID()}",
                                localUri = uri,
                                prompt = params.prompt,
                                mediaType = "IMAGE",
                                createdAt = System.currentTimeMillis(),
                            )
                        studioRepository.addEphemeralMedia(asset)
                        assetUris.add(asset.localUri)
                        assetIds.add(asset.id)
                        assetTypes.add("IMAGE")
                        assetPrompts.add(params.prompt)
                    }
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = buildResultJson(assetUris, assetIds, assetTypes, assetPrompts),
                    )
                },
                onFailure = { error ->
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = buildErrorJson(error.message ?: "Unknown error during image generation"),
                    )
                },
            )
        }

        private suspend fun executeVideo(request: ToolCallRequest): ToolExecutionResult {
            val params = request.parameters as GenerateVideoToolParams
            val settings =
                VideoGenerationSettings(
                    aspectRatio = AspectRatio.entries.find { it.ratio == params.aspect_ratio } ?: AspectRatio.ONE_ONE,
                    quality =
                        GenerationQuality.entries.find { it.name.equals(params.quality, ignoreCase = true) } ?: GenerationQuality.SPEED,
                    videoDuration = params.duration,
                    videoResolution = params.resolution,
                )

            loggingPort.debug(TAG, "Executing generate_video: prompt='${params.prompt}', settings=$settings")

            return generateVideoUseCase(params.prompt, settings).fold(
                onSuccess = { bytes ->
                    val uri = studioRepository.cacheEphemeralMedia(bytes, "VIDEO")
                    val asset =
                        com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                            id = "chat_media_${java.util.UUID.randomUUID()}",
                            localUri = uri,
                            prompt = params.prompt,
                            mediaType = "VIDEO",
                            createdAt = System.currentTimeMillis(),
                        )
                    studioRepository.addEphemeralMedia(asset)
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson =
                            buildResultJson(
                                uris = listOf(asset.localUri),
                                ids = listOf(asset.id),
                                types = listOf("VIDEO"),
                                prompts = listOf(params.prompt),
                            ),
                    )
                },
                onFailure = { error ->
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = buildErrorJson(error.message ?: "Unknown error during video generation"),
                    )
                },
            )
        }

        private suspend fun executeMusic(request: ToolCallRequest): ToolExecutionResult {
            val params = request.parameters as GenerateMusicToolParams
            val settings =
                MusicGenerationSettings(
                    duration = params.duration,
                )

            loggingPort.debug(TAG, "Executing generate_music: prompt='${params.prompt}', settings=$settings")

            return generateMusicUseCase(params.prompt, settings).fold(
                onSuccess = { bytes ->
                    val uri = studioRepository.cacheEphemeralMedia(bytes, "MUSIC")
                    val asset =
                        com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                            id = "chat_media_${java.util.UUID.randomUUID()}",
                            localUri = uri,
                            prompt = params.prompt,
                            mediaType = "MUSIC",
                            createdAt = System.currentTimeMillis(),
                        )
                    studioRepository.addEphemeralMedia(asset)
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson =
                            buildResultJson(
                                uris = listOf(asset.localUri),
                                ids = listOf(asset.id),
                                types = listOf("MUSIC"),
                                prompts = listOf(params.prompt),
                            ),
                    )
                },
                onFailure = { error ->
                    ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = buildErrorJson(error.message ?: "Unknown error during music generation"),
                    )
                },
            )
        }

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
