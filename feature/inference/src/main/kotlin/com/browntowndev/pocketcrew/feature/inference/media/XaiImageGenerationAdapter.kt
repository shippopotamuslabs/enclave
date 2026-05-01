package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.withClampedGenerationCount
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.core.JsonValue
import com.openai.errors.BadRequestException
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.util.Base64
import javax.inject.Inject
import okhttp3.RequestBody.Companion.toRequestBody

class XaiImageGenerationAdapter @Inject constructor(
    private val clientProvider: OpenAiClientProviderPort,
    private val okHttpClient: OkHttpClient
) {
    suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray? = null
    ): Result<List<ByteArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val visualSettings = settings as? VisualGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VisualGenerationSettings")
            val generationCount = (settings as? ImageGenerationSettings)
                ?.withClampedGenerationCount()
                ?.generationCount
                ?: 1

            if (referenceImage != null) {
                // xAI requires JSON format for /v1/images/edits, but the com.openai Java SDK
                // uses multipart/form-data for image edits. We must use a raw OkHttp request.
                return@runCatching performXaiImageEdit(
                    prompt = prompt,
                    apiKey = apiKey,
                    modelId = modelId,
                    baseUrl = baseUrl ?: "https://api.x.ai/v1",
                    generationCount = generationCount,
                    referenceImage = referenceImage
                )
            }

            // xAI uses OpenAI compatible API
            val client = clientProvider.getClient(apiKey, baseUrl ?: "https://api.x.ai/v1")
            val paramsBuilder = ImageGenerateParams.builder()
                .model(ImageModel.of(modelId))
                .prompt(prompt)
                .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
                .n(generationCount.toLong())
                .putAdditionalBodyProperty("aspect_ratio", JsonValue.from(visualSettings.aspectRatio.toXaiAspectRatio()))
                .putAdditionalBodyProperty("resolution", JsonValue.from(visualSettings.quality.toXaiResolution()))

            val params = paramsBuilder.build()

            val response = client.images().generate(params)
            if (response.isRejectedByModeration()) {
                throw XaiImageModerationRejectedException()
            }

            val images = response.data().orElse(emptyList())
                .mapNotNull { it.b64Json().orElse(null) }
                .map { Base64.getDecoder().decode(it) }
            if (images.isEmpty()) {
                throw IllegalStateException("No image data in response")
            }
            images
        }.recoverCatching { throwable ->
            if (throwable.isXaiPromptRejection()) {
                throw XaiImageModerationRejectedException(throwable)
            }
            throw throwable
        }
    }

    private fun performXaiImageEdit(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String,
        generationCount: Int,
        referenceImage: ByteArray
    ): List<ByteArray> {
        val base64Image = Base64.getEncoder().encodeToString(referenceImage)
        val dataUri = "data:image/png;base64,$base64Image"

        val jsonBody = """
            {
                "model": "$modelId",
                "prompt": "${prompt.replace("\"", "\\\"").replace("\n", "\\n")}",
                "n": $generationCount,
                "response_format": "b64_json",
                "image": {
                    "url": "$dataUri",
                    "type": "image_url"
                }
            }
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url("${baseUrl.trimEnd('/')}/images/edits")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val responseBody = response.body.string()
            if (response.code == HTTP_BAD_REQUEST && responseBody.contains("moderation", ignoreCase = true)) {
                throw XaiImageModerationRejectedException()
            }
            throw IllegalStateException("xAI edit request failed: ${response.code} $responseBody")
        }

        val responseBodyString = response.body.string()
        // Use regex or simple parsing to extract b64_json
        // Ideally we would use a JSON library, but since we're writing a raw client, we can parse it manually or use kotlinx.serialization
        // To be safe and avoid adding serialization imports that might not be available, we can parse with a regex for b64_json
        val b64Regex = """"b64_json"\s*:\s*"([^"]+)"""".toRegex()
        val matches = b64Regex.findAll(responseBodyString)
        
        val images = matches.map { matchResult ->
            Base64.getDecoder().decode(matchResult.groupValues[1])
        }.toList()

        if (images.isEmpty()) {
            throw IllegalStateException("No image data in response: $responseBodyString")
        }
        return images
    }

    private fun AspectRatio.toXaiAspectRatio(): String = when (this) {
        AspectRatio.TWENTY_ONE_NINE -> "20:9"
        AspectRatio.FIVE_FOUR -> "4:3"
        else -> ratio
    }

    private fun GenerationQuality.toXaiResolution(): String = when (this) {
        GenerationQuality.SPEED,
        GenerationQuality.LOW,
        GenerationQuality.MEDIUM -> "1k"
        GenerationQuality.QUALITY,
        GenerationQuality.HD,
        GenerationQuality.ULTRA,
        GenerationQuality.HIGH,
        GenerationQuality.AUTO -> "2k"
    }

    private fun com.openai.models.images.ImagesResponse.isRejectedByModeration(): Boolean =
        additionalBooleanProperty("passed_moderation") == false ||
            additionalBooleanProperty("respect_moderation") == false

    private fun com.openai.models.images.ImagesResponse.additionalBooleanProperty(name: String): Boolean? =
        _additionalProperties()[name]?.let { value ->
            runCatching { value.convert(Boolean::class.javaObjectType) }.getOrNull()
        }

    private fun Throwable.isXaiPromptRejection(): Boolean =
        this is BadRequestException && statusCode() == HTTP_BAD_REQUEST

    private class XaiImageModerationRejectedException(
        cause: Throwable? = null
    ) : IllegalStateException(MODERATION_REJECTION_MESSAGE, cause)

    private companion object {
        private const val HTTP_BAD_REQUEST = 400
        private const val MODERATION_REJECTION_MESSAGE = "Prompt rejected due to moderation."
    }
}
