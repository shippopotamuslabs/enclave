package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.withClampedGenerationCount
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.GenerateImagesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class GoogleImageGenerationAdapter @Inject constructor(
    private val clientProvider: GoogleGenAiClientProviderPort
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
            val config = GenerateImagesConfig.builder().apply {
                val ratio = when (visualSettings.aspectRatio) {
                    AspectRatio.ONE_ONE -> "1:1"
                    AspectRatio.THREE_FOUR -> "3:4"
                    AspectRatio.FOUR_THREE -> "4:3"
                    AspectRatio.NINE_SIXTEEN -> "9:16"
                    AspectRatio.SIXTEEN_NINE -> "16:9"
                    else -> "1:1"
                }
                aspectRatio(ratio)
                numberOfImages(generationCount)
                // Add quality/size if supported by the specific model
            }.build()

            val response = generateImages(client, modelId, prompt, config)
            val images = response.generatedImages().orElse(emptyList())
                .mapNotNull { generatedImage ->
                    generatedImage.image().orElse(null)?.imageBytes()?.orElse(null)
                }
            if (images.isEmpty()) {
                throw IllegalStateException("No image generated")
            }
            images
        }
    }

    protected open fun generateImages(
        client: Client,
        modelId: String,
        prompt: String,
        config: GenerateImagesConfig,
    ): GenerateImagesResponse = client.models.generateImages(modelId, prompt, config)
}
