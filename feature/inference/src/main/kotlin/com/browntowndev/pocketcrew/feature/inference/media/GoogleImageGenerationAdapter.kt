package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import com.google.genai.interactions.models.interactions.CreateModelInteractionParams
import com.google.genai.interactions.models.interactions.Interaction
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
        settings: GenerationSettings,
        referenceImage: ByteArray? = null
    ): Result<List<ByteArray>> = withContext(Dispatchers.IO) {
        runCatching<List<ByteArray>> {
            val client = clientProvider.getClient(apiKey, baseUrl)

            val params = CreateModelInteractionParams.builder()
                .model(modelId)
                .input(prompt)
                .build()
            val interaction = createInteraction(client, params)
            
            val outputs = try {
                val method = interaction.javaClass.methods.find { it.name == "outputs" }
                val optional = method?.invoke(interaction) as? java.util.Optional<List<Any>>
                optional?.orElse(emptyList()) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val images = outputs.mapNotNull { content ->
                try {
                    val method = content.javaClass.methods.find { it.name == "image" }
                    val optional = method?.invoke(content) as? java.util.Optional<Any>
                    optional?.orElse(null)
                } catch (e: Exception) {
                    null
                }
            }.mapNotNull { imageObj ->
                try {
                    val method = imageObj.javaClass.methods.find { it.name == "imageBytes" }
                    val optional = method?.invoke(imageObj) as? java.util.Optional<ByteArray>
                    optional?.orElse(null)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (images.isEmpty()) {
                throw IllegalStateException("No image generated")
            }
            images
        }
    }

    protected open fun createInteraction(
        client: Client,
        params: CreateModelInteractionParams,
    ): Interaction = client.interactions.create(params)
}
