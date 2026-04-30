package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.withClampedGenerationCount
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import com.google.genai.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Optional
import javax.inject.Inject

open class GoogleImageGenerationAdapter @Inject constructor(
    private val clientProvider: GoogleGenAiClientProviderPort
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
            val client = clientProvider.getClient(apiKey, baseUrl)
            val ratio = when (visualSettings.aspectRatio) {
                AspectRatio.ONE_ONE -> "1:1"
                AspectRatio.THREE_FOUR -> "3:4"
                AspectRatio.FOUR_THREE -> "4:3"
                AspectRatio.NINE_SIXTEEN -> "9:16"
                AspectRatio.SIXTEEN_NINE -> "16:9"
                else -> "1:1"
            }

            val response = if (referenceImage != null) {
                // Imagen 3 requires [1] in the prompt to link to the reference image
                val finalPrompt = if (prompt.contains("[1]")) prompt else "$prompt [1]"
                
                val config = EditImageConfig.builder()
                    .aspectRatio(ratio)
                    .numberOfImages(generationCount)
                    .editMode("EDIT_MODE_DEFAULT")
                    .build()
                
                val rawImage = RawReferenceImage.builder()
                    .referenceImage(Image.builder().imageBytes(referenceImage).build())
                    .referenceType("raw")
                    .build()
                
                val editResponse = editImage(client, modelId, finalPrompt, rawImage, config)
                GenerateImagesResponse.builder()
                    .generatedImages(editResponse.generatedImages().orElse(emptyList()))
                    .build()
            } else {
                val config = GenerateImagesConfig.builder()
                    .aspectRatio(ratio)
                    .numberOfImages(generationCount)
                    .build()
                
                generateImages(client, modelId, prompt, config)
            }

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

    protected open fun editImage(
        client: Client,
        modelId: String,
        prompt: String,
        referenceImage: RawReferenceImage,
        config: EditImageConfig,
    ): EditImageResponse = client.models.editImage(modelId, prompt, listOf(referenceImage), config)
}
