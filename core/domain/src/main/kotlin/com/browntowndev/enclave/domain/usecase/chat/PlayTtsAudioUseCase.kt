package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.enclave.domain.port.media.AudioPlayerPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import javax.inject.Inject

class PlayTtsAudioUseCase
    @Inject
    constructor(
        private val resolveAssignedModelSelectionUseCase: ResolveAssignedModelSelectionUseCase,
        private val ttsServiceFactory: TtsServiceFactoryPort,
        private val audioPlayer: AudioPlayerPort,
        private val apiKeyProvider: ApiKeyProviderPort,
    ) {
        @Suppress("ThrowsCount")
        suspend operator fun invoke(text: String): Result<Unit> =
            Result.runCatching {
                val selection =
                    resolveAssignedModelSelectionUseCase(ModelType.TTS)
                        ?: throw IllegalStateException("No TTS provider assigned.")

                val ttsAsset =
                    selection.ttsAsset
                        ?: throw IllegalStateException("Assigned TTS model selection is invalid.")

                val apiKey =
                    apiKeyProvider.getApiKey(ttsAsset.credentialAlias)
                        ?: throw IllegalStateException("API key not found for TTS provider: ${ttsAsset.displayName}")

                val ttsService =
                    ttsServiceFactory.create(
                        provider = ttsAsset.provider,
                        apiKey = apiKey,
                        baseUrl = ttsAsset.baseUrl,
                    )

                val audioBytes =
                    ttsService
                        .synthesizeSpeech(
                            text = text,
                            voice = ttsAsset.voiceName,
                            modelId = ttsAsset.modelName,
                        ).getOrThrow()

                audioPlayer.playAudio(audioBytes)
            }

        fun stop() {
            audioPlayer.stop()
        }
    }
