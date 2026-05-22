package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.GenerateImageToolParams
import com.browntowndev.enclave.domain.model.inference.GenerateMusicToolParams
import com.browntowndev.enclave.domain.model.inference.GenerateVideoToolParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings

object MediaGenerationRequestMapper {
    fun fromToolCall(request: ToolCallRequest): MediaGenerationRequest =
        when (request.toolName) {
            ToolDefinition.GENERATE_IMAGE.name -> {
                val params = request.parameters as GenerateImageToolParams
                MediaGenerationRequest(
                    prompt = params.prompt,
                    capability = MediaCapability.IMAGE,
                    settings =
                        ImageGenerationSettings(
                            aspectRatio =
                                AspectRatio.entries.find { it.ratio == params.aspect_ratio }
                                    ?: AspectRatio.ONE_ONE,
                            quality =
                                GenerationQuality.entries.find {
                                    it.name.equals(params.quality, ignoreCase = true)
                                } ?: GenerationQuality.SPEED,
                            generationCount = params.count.coerceIn(1, 4),
                        ),
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                )
            }
            ToolDefinition.GENERATE_VIDEO.name -> {
                val params = request.parameters as GenerateVideoToolParams
                MediaGenerationRequest(
                    prompt = params.prompt,
                    capability = MediaCapability.VIDEO,
                    settings =
                        VideoGenerationSettings(
                            aspectRatio =
                                AspectRatio.entries.find { it.ratio == params.aspect_ratio }
                                    ?: AspectRatio.ONE_ONE,
                            quality =
                                GenerationQuality.entries.find {
                                    it.name.equals(params.quality, ignoreCase = true)
                                } ?: GenerationQuality.SPEED,
                            videoDuration = params.duration,
                            videoResolution = params.resolution,
                        ),
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                )
            }
            ToolDefinition.GENERATE_MUSIC.name -> {
                val params = request.parameters as GenerateMusicToolParams
                MediaGenerationRequest(
                    prompt = params.prompt,
                    capability = MediaCapability.MUSIC,
                    settings = MusicGenerationSettings(duration = params.duration),
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                )
            }
            else -> throw IllegalArgumentException("Unsupported media tool: ${request.toolName}")
        }

    fun fromStudio(
        prompt: String,
        capability: MediaCapability,
        settings: GenerationSettings,
    ): MediaGenerationRequest =
        MediaGenerationRequest(
            prompt = prompt,
            capability = capability,
            settings = settings,
            origin = MediaGenerationOrigin.STUDIO_FOREGROUND,
        )
}
