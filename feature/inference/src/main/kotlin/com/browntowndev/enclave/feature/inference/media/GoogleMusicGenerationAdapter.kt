package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal object LyriaPromptDefaults {
    const val CLIP_MAX_DURATION_SECONDS = 30
}

internal fun buildLyriaPrompt(
    userPrompt: String,
    settings: MusicGenerationSettings,
    modelId: String,
): String {
    val durationSeconds =
        if (modelId.contains("clip", ignoreCase = true)) {
            LyriaPromptDefaults.CLIP_MAX_DURATION_SECONDS
        } else {
            settings.duration
        }
    return "Create a ${durationSeconds}-second track at ${settings.tempo} BPM. $userPrompt"
}

open class GoogleMusicGenerationAdapter
    @Inject
    constructor(
        private val clientProvider: GoogleGenAiClientProviderPort,
    ) {
        suspend fun generateMusic(
            prompt: String,
            apiKey: String,
            modelId: String,
            baseUrl: String?,
            settings: GenerationSettings,
        ): Result<ByteArray> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val musicSettings =
                        settings as? MusicGenerationSettings
                            ?: throw IllegalArgumentException("Settings must be MusicGenerationSettings")

                    val client = clientProvider.getClient(apiKey, baseUrl)
                    val enrichedPrompt = buildLyriaPrompt(prompt, musicSettings, modelId)

                    // Lyria models via generateContent return parts with text and inlineData containing audio bytes.
                    val response = generateContent(client, modelId, enrichedPrompt)

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
            prompt: String,
        ) = client.models.generateContent(modelId, prompt, null)
    }
