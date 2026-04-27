package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.inject.Inject

class OpenAiImageGenerationAdapter @Inject constructor(
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
            val client = clientProvider.getClient(apiKey, baseUrl)
            val params = ImageGenerateParams.builder()
                .model(ImageModel.of(modelId))
                .prompt(prompt)
                .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
                .apply {
                    val size = when (visualSettings.aspectRatio) {
                        AspectRatio.SIXTEEN_NINE -> ImageGenerateParams.Size._1792X1024
                        AspectRatio.NINE_SIXTEEN -> ImageGenerateParams.Size._1024X1792
                        else -> ImageGenerateParams.Size._1024X1024
                    }
                    size(size)
                    
                    val quality = when (visualSettings.quality) {
                        GenerationQuality.HD -> ImageGenerateParams.Quality.HD
                        else -> ImageGenerateParams.Quality.STANDARD
                    }
                    quality(quality)
                }
                .build()

            val response = client.images().generate(params)
            val b64 = response.data().get().firstOrNull()?.b64Json()?.get()
                ?: throw IllegalStateException("No image data in response")
            
            Base64.getDecoder().decode(b64)
        }
    }
}
