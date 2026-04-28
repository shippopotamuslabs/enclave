package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.core.JsonValue
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
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val visualSettings = settings as? VisualGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VisualGenerationSettings")
            // xAI uses OpenAI compatible API
            val client = clientProvider.getClient(apiKey, baseUrl ?: "https://api.x.ai/v1")
            val params = ImageGenerateParams.builder()
                .model(ImageModel.of(modelId))
                .prompt(prompt)
                .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
                .putAdditionalBodyProperty("aspect_ratio", JsonValue.from(visualSettings.aspectRatio.toXaiAspectRatio()))
                .putAdditionalBodyProperty("resolution", JsonValue.from(visualSettings.quality.toXaiResolution()))
                .build()

            val response = client.images().generate(params)
            val b64 = response.data().get().firstOrNull()?.b64Json()?.get()
                ?: throw IllegalStateException("No image data in response")
            
            Base64.getDecoder().decode(b64)
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
}
