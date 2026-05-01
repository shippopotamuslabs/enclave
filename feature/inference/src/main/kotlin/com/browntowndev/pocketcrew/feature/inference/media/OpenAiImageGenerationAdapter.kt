package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.*
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.images.*
import kotlinx.coroutines.*
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
        settings: GenerationSettings,
        referenceImage: ByteArray? = null
    ): Result<List<ByteArray>> = withContext(Dispatchers.IO) {
        runCatching {
            if (referenceImage != null) {
                throw UnsupportedOperationException("OpenAI does not support image-to-image generation with text prompts.")
            }

            val visualSettings = settings as? VisualGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VisualGenerationSettings")
            val generationCount = (settings as? ImageGenerationSettings)
                ?.withClampedGenerationCount()
                ?.generationCount
                ?: 1
            val client = clientProvider.getClient(apiKey, baseUrl)
            
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
                .n(generationCount.toLong())
                .apply {
                    val size = when (visualSettings.aspectRatio) {
                        AspectRatio.SIXTEEN_NINE -> ImageGenerateParams.Size.of("1536x1024")
                        AspectRatio.NINE_SIXTEEN -> ImageGenerateParams.Size.of("1024x1536")
                        else -> ImageGenerateParams.Size._1024X1024
                    }
                    size(size)
                    
                    val quality = when (visualSettings.quality) {
                        GenerationQuality.HD, GenerationQuality.HIGH -> ImageGenerateParams.Quality.of("high")
                        GenerationQuality.LOW -> ImageGenerateParams.Quality.of("low")
                        GenerationQuality.MEDIUM -> ImageGenerateParams.Quality.of("medium")
                        GenerationQuality.AUTO -> ImageGenerateParams.Quality.of("auto")
                        else -> ImageGenerateParams.Quality.of("auto")
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
