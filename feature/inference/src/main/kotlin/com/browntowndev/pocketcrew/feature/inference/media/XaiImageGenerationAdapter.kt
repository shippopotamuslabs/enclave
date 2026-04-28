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
import java.util.Base64
import javax.inject.Inject

class XaiImageGenerationAdapter @Inject constructor(
    private val clientProvider: OpenAiClientProviderPort
) {
    suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings
    ): Result<List<ByteArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val visualSettings = settings as? VisualGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VisualGenerationSettings")
            val generationCount = (settings as? ImageGenerationSettings)
                ?.withClampedGenerationCount()
                ?.generationCount
                ?: 1
            // xAI uses OpenAI compatible API
            val client = clientProvider.getClient(apiKey, baseUrl ?: "https://api.x.ai/v1")
            val params = ImageGenerateParams.builder()
                .model(ImageModel.of(modelId))
                .prompt(prompt)
                .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
                .n(generationCount.toLong())
                .putAdditionalBodyProperty("aspect_ratio", JsonValue.from(visualSettings.aspectRatio.toXaiAspectRatio()))
                .putAdditionalBodyProperty("resolution", JsonValue.from(visualSettings.quality.toXaiResolution()))
                .build()

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

    private fun AspectRatio.toXaiAspectRatio(): String = when (this) {
        AspectRatio.TWENTY_ONE_NINE -> "20:9"
        AspectRatio.FIVE_FOUR -> "4:3"
        else -> ratio
    }

    private fun GenerationQuality.toXaiResolution(): String = when (this) {
        GenerationQuality.SPEED -> "1k"
        GenerationQuality.QUALITY,
        GenerationQuality.HD,
        GenerationQuality.ULTRA -> "2k"
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
