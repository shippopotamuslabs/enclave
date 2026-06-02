package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.ImageInputRequirement
import com.browntowndev.enclave.domain.model.media.ModelGenerationInputSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object FalOpenApiInputSchemaParser {
    fun parse(openApiDocument: JsonObject): ModelGenerationInputSchema? {
        val schemas = openApiDocument["components"]?.jsonObject?.get("schemas")?.jsonObject ?: return null
        val inputSchema =
            schemas.entries
                .map { it.value.jsonObject }
                .firstOrNull { schema ->
                    schema["properties"]?.jsonObject?.containsKey("prompt") == true
                } ?: return null
        val inputProperties = inputSchema["properties"]?.jsonObject ?: return null
        val requiredArray = inputSchema["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

        val aspectRatioOptions = stringEnumOptions(inputProperties, "aspect_ratio")
        val resolutionOptions = stringEnumOptions(inputProperties, "resolution")
        val durationTokens = stringEnumOptions(inputProperties, "duration")
        val durationSeconds = intEnumOptions(inputProperties, "duration")
        val numImagesRange = intRange(inputProperties, "num_images")
        val musicDurationRange = intRange(inputProperties, "duration", "music_duration", "length")
        val musicTempoRange = intRange(inputProperties, "tempo", "bpm")

        val imageFields = listOf("image_url", "image", "reference_image", "start_image_url")
        val foundImageField = imageFields.firstOrNull { inputProperties.containsKey(it) }

        val imageInput =
            when {
                foundImageField == null -> ImageInputRequirement.NONE
                requiredArray.contains(foundImageField) -> ImageInputRequirement.REQUIRED
                else -> ImageInputRequirement.OPTIONAL
            }

        return ModelGenerationInputSchema(
            aspectRatioOptions = aspectRatioOptions,
            resolutionOptions = resolutionOptions,
            videoDurationSeconds = durationSeconds,
            videoDurationTokens = durationTokens,
            numImagesRange = numImagesRange,
            musicDurationRange = musicDurationRange,
            musicTempoRange = musicTempoRange,
            imageInput = imageInput,
        )
    }

    private fun stringEnumOptions(
        properties: JsonObject,
        vararg keys: String,
    ): List<String> =
        keys
            .firstNotNullOfOrNull { key -> properties[key]?.enumValues() }
            .orEmpty()

    private fun intEnumOptions(
        properties: JsonObject,
        vararg keys: String,
    ): List<Int> =
        keys
            .firstNotNullOfOrNull { key -> properties[key]?.enumValues()?.mapNotNull { it.toIntOrNull() } }
            .orEmpty()

    private fun intRange(
        properties: JsonObject,
        vararg keys: String,
    ): IntRange? {
        for (key in keys) {
            val property = properties[key]?.jsonObject ?: continue
            val minimum = property["minimum"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val maximum = property["maximum"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (minimum != null && maximum != null && minimum <= maximum) {
                return minimum..maximum
            }
            val enumInts = properties[key]?.enumValues()?.mapNotNull { it.toIntOrNull() }.orEmpty()
            if (enumInts.isNotEmpty()) {
                return enumInts.min()..enumInts.max()
            }
        }
        return null
    }

    private fun JsonElement.enumValues(): List<String> =
        when (this) {
            is JsonObject ->
                this["enum"]
                    ?.jsonArray
                    ?.mapNotNull { element ->
                        when (element) {
                            is JsonPrimitive -> element.contentOrNull
                            else -> null
                        }
                    }.orEmpty()
            else -> emptyList()
        }
}
