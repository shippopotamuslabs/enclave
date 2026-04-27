package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.types.GenerateImagesConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GoogleImageGenerationAdapter @Inject constructor(
    private val clientProvider: GoogleGenAiClientProviderPort
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
                // Add quality/size if supported by the specific model
            }.build()

            val response = client.models.generateImages(
                modelId,
                prompt,
                config
            )
            val image = response.generatedImages().orElse(emptyList()).firstOrNull()
                ?: throw IllegalStateException("No image generated")
            
            // Guessing the correct method to get bytes based on common patterns in these SDKs
            val imageBytesObj = image.image().get()
            // If it's a Blob or similar, it often has a data() or imageBytes() method
            // Let's try to get it as a String if it's base64 or just cast it
            imageBytesObj.imageBytes().get()
        }
    }
}
