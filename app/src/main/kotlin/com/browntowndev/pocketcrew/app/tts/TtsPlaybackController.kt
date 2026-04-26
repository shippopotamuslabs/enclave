package com.browntowndev.pocketcrew.app.tts

import android.content.Context
import android.content.Intent
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRegistryPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.port.media.toPlaybackRequest
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level implementation of [TtsPlaybackControllerPort] that drives Media3 playback.
 *
 * This controller:
 * 1. Resolves the assigned TTS provider and API key
 * 2. Registers the request in the [TtsPlaybackRegistryPort]
 * 3. Starts the [TtsPlaybackService] with the request ID
 * 4. ExoPlayer reads the audio via [TtsDataSource] which resolves the URI
 * 5. Emits playback status through a [Flow]
 *
 * The ViewModel calls [play] which returns a [Flow] of [TtsPlaybackStatus],
 * and [stop] to cancel playback.
 */
@Singleton
class TtsPlaybackController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val resolveAssignedModelSelectionUseCase: ResolveAssignedModelSelectionUseCase,
        private val apiKeyProvider: ApiKeyProviderPort,
        private val registry: TtsPlaybackRegistryPort,
        private val logger: LoggingPort,
    ) : TtsPlaybackControllerPort {
        private val statusFlow = MutableStateFlow<TtsPlaybackStatus>(TtsPlaybackStatus.Initializing)
        private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var currentRequestId: String? = null
        private var playbackJob: Job? = null
        private var statusJob: Job? = null

        override fun play(text: String): Flow<TtsPlaybackStatus> {
            // Cancel any existing playback before publishing the state for the new request.
            stopPlayback(emitStopped = false)
            statusFlow.value = TtsPlaybackStatus.Initializing

            playbackJob =
                controllerScope.launch {
                    try {
                        val selection = resolveAssignedModelSelectionUseCase(ModelType.TTS)
                        if (selection == null) {
                            statusFlow.value = TtsPlaybackStatus.Error("No TTS provider assigned.")
                            return@launch
                        }

                        val ttsAsset = selection.ttsAsset
                        if (ttsAsset == null) {
                            statusFlow.value = TtsPlaybackStatus.Error("Assigned TTS model selection is invalid.")
                            return@launch
                        }

                        val apiKey = apiKeyProvider.getApiKey(ttsAsset.credentialAlias)
                        if (apiKey == null) {
                            statusFlow.value = TtsPlaybackStatus.Error("API key not found for TTS provider: ${ttsAsset.displayName}")
                            return@launch
                        }

                        val requestId = UUID.randomUUID().toString()
                        val request =
                            ttsAsset.toPlaybackRequest(
                                requestId = requestId,
                                text = text,
                            )

                        registry.register(request)
                        currentRequestId = requestId
                        observePlaybackStatus(requestId)

                        // Start the Media3 playback service
                        withContext(Dispatchers.Main) {
                            startPlaybackService(requestId)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(TAG, "TTS playback error: ${e.message}", e)
                        statusFlow.value = TtsPlaybackStatus.Error(e.message ?: "Unknown error", e)
                    }
                }

            return statusFlow.asStateFlow()
        }

        override fun stop() {
            stopPlayback(emitStopped = true)
        }

        private fun stopPlayback(emitStopped: Boolean) {
            playbackJob?.cancel()
            playbackJob = null
            statusJob?.cancel()
            statusJob = null

            currentRequestId?.let { requestId ->
                registry.publishStatus(requestId, TtsPlaybackStatus.Stopped)
                registry.remove(requestId)
            }
            currentRequestId = null
            stopPlaybackService()

            if (emitStopped) {
                statusFlow.value = TtsPlaybackStatus.Stopped
            }
        }

        private fun observePlaybackStatus(requestId: String) {
            statusJob?.cancel()
            val flow = registry.observeStatus(requestId)
            statusJob =
                if (flow == null) {
                    null
                } else {
                    controllerScope.launch {
                        flow.collect { status ->
                            statusFlow.value = status
                            if (status.isTerminal()) {
                                currentRequestId = null
                                statusJob?.cancel()
                                statusJob = null
                            }
                        }
                    }
                }
        }

        private fun startPlaybackService(requestId: String) {
            val intent =
                Intent(context, TtsPlaybackService::class.java).apply {
                    action = TtsPlaybackService.ACTION_PLAY_REQUEST
                    putExtra(TtsPlaybackService.EXTRA_REQUEST_ID, requestId)
                }
            context.startForegroundService(intent)
        }

        private fun stopPlaybackService() {
            val intent = Intent(context, TtsPlaybackService::class.java)
            context.stopService(intent)
        }

        private fun TtsPlaybackStatus.isTerminal(): Boolean =
            this is TtsPlaybackStatus.Completed ||
                this is TtsPlaybackStatus.Error ||
                this is TtsPlaybackStatus.Stopped

        companion object {
            private const val TAG = "TtsPlaybackCtrl"
        }
    }
