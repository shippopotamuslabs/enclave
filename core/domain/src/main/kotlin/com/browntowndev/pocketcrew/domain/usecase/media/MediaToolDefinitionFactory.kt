package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.inference.GenerateImageToolParams
import com.browntowndev.pocketcrew.domain.model.inference.GenerateMusicToolParams
import com.browntowndev.pocketcrew.domain.model.inference.GenerateVideoToolParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolSchema
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

class MediaToolDefinitionFactory @Inject constructor(
    private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
) {
    fun buildImageToolDefinition(providerName: String?): ToolDefinition {
        val caps = getProviderCapabilitiesUseCase(providerName)
        val properties = buildJsonObject {
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "A detailed description of the image to generate.")
            }
            putJsonObject("aspect_ratio") {
                put("type", "string")
                put("description", "Aspect ratio for the generated image.")
                putJsonArray("enum") {
                    caps.supportedAspectRatios.forEach { add(JsonPrimitive(it.ratio)) }
                }
            }
            putJsonObject("quality") {
                put("type", "string")
                put("description", "Quality preset for generation.")
                putJsonArray("enum") {
                    caps.supportedImageQualities.forEach { add(JsonPrimitive(it.name.lowercase())) }
                }
            }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "Number of images to generate (1-4).")
                put("minimum", 1)
                put("maximum", 4)
            }
        }
        
        @Suppress("MaxLineLength")
        return ToolDefinition(
            name = "generate_image",
            description = "Generate an image from a text description. " +
                "Use this when the user asks you to create, draw, or generate an image, illustration, or picture.",
            parametersClass = GenerateImageToolParams::class,
            schemaOverride = ToolSchema(properties, listOf("prompt"))
        )
    }

    fun buildVideoToolDefinition(providerName: String?): ToolDefinition {
        val caps = getProviderCapabilitiesUseCase(providerName)
        val properties = buildJsonObject {
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "A detailed description of the video to generate.")
            }
            putJsonObject("aspect_ratio") {
                put("type", "string")
                put("description", "Aspect ratio for the generated video.")
                putJsonArray("enum") {
                    caps.supportedAspectRatios.forEach { add(JsonPrimitive(it.ratio)) }
                }
            }
            putJsonObject("quality") {
                put("type", "string")
                put("description", "Quality preset for generation.")
                putJsonArray("enum") {
                    caps.supportedVideoQualities.forEach { add(JsonPrimitive(it.name.lowercase())) }
                }
            }
            putJsonObject("duration") {
                put("type", "integer")
                put("description", "Video duration in seconds.")
                putJsonArray("enum") {
                    caps.supportedVideoDurations.forEach { add(JsonPrimitive(it)) }
                }
            }
            putJsonObject("resolution") {
                put("type", "string")
                put("description", "Video resolution.")
                putJsonArray("enum") {
                    caps.supportedVideoResolutions.forEach { add(JsonPrimitive(it)) }
                }
            }
        }

        @Suppress("MaxLineLength")
        return ToolDefinition(
            name = "generate_video",
            description = "Generate a video from a text description. " +
                "Use this when the user asks you to create or generate a video or animation.",
            parametersClass = GenerateVideoToolParams::class,
            schemaOverride = ToolSchema(properties, listOf("prompt"))
        )
    }

    fun buildMusicToolDefinition(): ToolDefinition {
        // Music currently has no provider-specific parameters in the schema
        @Suppress("MaxLineLength")
        return ToolDefinition.GENERATE_MUSIC.copy(
            description = "Generate music or audio from a text description. " +
                "Use this when the user asks you to create, compose, or generate music, a beat, or a soundtrack."
        )
    }
}
