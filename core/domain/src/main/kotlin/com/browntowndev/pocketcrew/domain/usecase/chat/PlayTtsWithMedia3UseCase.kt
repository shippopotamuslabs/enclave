package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case that drives TTS playback through Media3 via the [TtsPlaybackControllerPort].
 *
 * This use case:
 * 1. Resolves the assigned TTS provider.
 * 2. Delegates to the controller port which registers the request, starts the Media3
 *    service, and emits playback status.
 *
 * If no TTS provider is assigned, emits [TtsPlaybackStatus.Error] and never starts Media3.
 */
class PlayTtsWithMedia3UseCase @Inject constructor(
    private val resolveAssignedModelSelectionUseCase: ResolveAssignedModelSelectionUseCase,
    private val playbackController: TtsPlaybackControllerPort,
    private val apiKeyProvider: ApiKeyProviderPort,
) {
    /**
     * Plays TTS audio for the given [text] using Media3 playback.
     *
     * @return A [Flow] of [TtsPlaybackStatus] representing the playback lifecycle.
     */
    operator fun invoke(text: String): Flow<TtsPlaybackStatus> = flow {
        emit(TtsPlaybackStatus.Initializing)

        val selection = resolveAssignedModelSelectionUseCase(ModelType.TTS)
        if (selection == null) {
            emit(TtsPlaybackStatus.Error("No TTS provider assigned."))
            return@flow
        }

        val ttsAsset = selection.ttsAsset
        if (ttsAsset == null) {
            emit(TtsPlaybackStatus.Error("Assigned TTS model selection is invalid."))
            return@flow
        }

        val apiKey = apiKeyProvider.getApiKey(ttsAsset.credentialAlias)
        if (apiKey == null) {
            emit(TtsPlaybackStatus.Error("API key not found for TTS provider: ${ttsAsset.displayName}"))
            return@flow
        }

        // Delegate to the playback controller which handles Media3 lifecycle
        playbackController.play(text).collect { status ->
            emit(status)
        }
    }

    /**
     * Stops any currently active TTS playback.
     */
    fun stop() {
        playbackController.stop()
    }
}
