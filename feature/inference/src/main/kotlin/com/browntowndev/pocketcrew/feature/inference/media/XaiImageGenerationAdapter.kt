package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
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
                .apply {
                    // xAI supports more flexible aspect ratios than OpenAI DALL-E 3
                    // Mapping to the strings supported by xAI/Flux
                    val sizeStr = when (visualSettings.aspectRatio) {
                        AspectRatio.ONE_ONE -> "1024x1024"
                        AspectRatio.NINE_SIXTEEN -> "1024x1792"
                        AspectRatio.SIXTEEN_NINE -> "1792x1024"
                        AspectRatio.THREE_FOUR -> "768x1024"
                        AspectRatio.FOUR_THREE -> "1024x768"
                        AspectRatio.TWO_THREE -> "683x1024"
                        AspectRatio.THREE_TWO -> "1024x683"
                    }
                    size(ImageGenerateParams.Size.of(sizeStr))
                    
                    // quality currently a no-op in many xAI integrations but mapping standard/hd
                    // to whatever the underlying model (Flux/Grok-2) might use if exposed.
                }
                .build()

            val response = client.images().generate(params)
            val b64 = response.data().get().firstOrNull()?.b64Json()?.get()
                ?: throw IllegalStateException("No image data in response")
            
            Base64.getDecoder().decode(b64)
        }
    }
}
