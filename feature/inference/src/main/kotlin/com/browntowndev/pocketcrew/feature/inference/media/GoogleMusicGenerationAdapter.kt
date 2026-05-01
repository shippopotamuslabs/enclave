package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class GoogleMusicGenerationAdapter @Inject constructor(
    private val clientProvider: GoogleGenAiClientProviderPort
) {
    suspend fun generateMusic(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientProvider.getClient(apiKey, baseUrl)
            
            // Lyria models via generateContent return parts with text and inlineData containing audio bytes.
            val response = generateContent(client, modelId, prompt)
            
            var audioBytes: ByteArray? = null
            response.parts()?.forEach { part ->
                val inlineData = part.inlineData().orElse(null)
                if (inlineData != null) {
                    audioBytes = inlineData.data().orElse(null)
                }
            }
            
            audioBytes ?: throw IllegalStateException("No audio data returned from generation")
        }
    }

    protected open fun generateContent(
        client: Client,
        modelId: String,
        prompt: String
    ) = client.models.generateContent(modelId, prompt, null)
}
