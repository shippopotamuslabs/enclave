package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.withClampedGenerationCount
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    ): Result<List<ByteArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val visualSettings = settings as? VisualGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VisualGenerationSettings")
            val generationCount = (settings as? ImageGenerationSettings)
                ?.withClampedGenerationCount()
                ?.generationCount
                ?: 1
            val client = clientProvider.getClient(apiKey, baseUrl)
            if (modelId.equals("dall-e-3", ignoreCase = true) && generationCount > 1) {
                return@runCatching coroutineScope {
                    (1..generationCount).map {
                        async {
                            val params = createParams(modelId, prompt, visualSettings, generationCount = 1)
                            val response = client.images().generate(params)
                            response.decodeImages().single()
                        }
                    }.awaitAll()
                }
            }

            val params = createParams(modelId, prompt, visualSettings, generationCount)
            val response = client.images().generate(params)
            response.decodeImages()
        }
    }

    private fun createParams(
        modelId: String,
        prompt: String,
        visualSettings: VisualGenerationSettings,
        generationCount: Int,
    ): ImageGenerateParams =
        ImageGenerateParams.builder()
                .model(ImageModel.of(modelId))
                .prompt(prompt)
                .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
                .n(generationCount.toLong())
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

    private fun com.openai.models.images.ImagesResponse.decodeImages(): List<ByteArray> {
        val images = data().orElse(emptyList())
            .mapNotNull { it.b64Json().orElse(null) }
            .map { Base64.getDecoder().decode(it) }
        if (images.isEmpty()) {
            throw IllegalStateException("No image data in response")
        }
        return images
    }
}
