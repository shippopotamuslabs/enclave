package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureEvent
import com.browntowndev.enclave.domain.port.media.StreamingAudioConfig
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionUseCase
import com.browntowndev.enclave.domain.usecase.chat.PersistVoiceTurnUseCase
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import com.browntowndev.enclave.feature.inference.media.RealtimeAudioCaptureGain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.sqrt

class LiveVoiceCoordinator
    @Inject
    constructor(
        private val liveVoiceSessionUseCase: LiveVoiceSessionUseCase,
        private val persistVoiceTurnUseCase: PersistVoiceTurnUseCase,
        private val realtimeAudioCapture: RealtimeAudioCapturePort,
        private val streamingAudioPlayer: StreamingAudioPlayerPort,
        private val errorHandler: ViewModelErrorHandler,
        private val loggingPort: LoggingPort,
    ) {
        private val _state = MutableStateFlow<LiveVoiceUiState>(LiveVoiceUiState.Idle)
        val state: StateFlow<LiveVoiceUiState> = _state.asStateFlow()

        var onChatIdPersisted: ((ChatId) -> Unit)? = null

        private val lifecycleMutex = Mutex()
        private var activeScope: CoroutineScope? = null
        private var session: LiveVoiceSession? = null
        private var captureJob: Job? = null
        private var eventsJob: Job? = null
        private var startJob: Job? = null
        private var endJob: Job? = null
        private var sessionGeneration: Int = 0
        private var isTearingDown: Boolean = false

        private var chatId: ChatId? = null
        private var userCaption: String = ""
        private var assistantCaption: String = ""
        private var isMuted: Boolean = false
        private var captureSampleRateHz: Int = CAPTURE_SAMPLE_RATE_OPENAI_HZ
        private var playbackConfig: StreamingAudioConfig = StreamingAudioConfig.PCM_16_24KHZ_MONO
        private var playerInitialized: Boolean = false
        private var playbackStarted: Boolean = false
        private var captureAudioSessionId: Int? = null
        private var isEchoCancellationActive: Boolean = false
        private var userBargeInActive: Boolean = false
        private var suppressAssistantOutput: Boolean = false
        private var bargeInHighEnergyFrameCount: Int = 0
        private var assistantPlaybackBaselineRms: Float = 0f
        private var isAssistantResponseActive: Boolean = false

        fun start(
            scope: CoroutineScope,
            chatId: ChatId?,
        ) {
            scope.launch {
                lifecycleMutex.withLock {
                    if (endJob?.isActive == true) {
                        endJob?.join()
                    }
                    teardown(resetToIdle = false)
                    isTearingDown = false
                    sessionGeneration += 1
                    val generation = sessionGeneration

                    activeScope = scope
                    this@LiveVoiceCoordinator.chatId = chatId
                    userCaption = ""
                    assistantCaption = ""
                    isMuted = false
                    playerInitialized = false
                    playbackStarted = false
                    captureAudioSessionId = null
                    isEchoCancellationActive = false
                    userBargeInActive = false
                    suppressAssistantOutput = false
                    bargeInHighEnergyFrameCount = 0
                    assistantPlaybackBaselineRms = 0f
                    isAssistantResponseActive = false
                    _state.value = LiveVoiceUiState.Connecting

                    startJob =
                        scope.launch(
                            errorHandler.coroutineExceptionHandler(TAG, "Failed to start Live Voice"),
                        ) {
                            liveVoiceSessionUseCase().fold(
                                onSuccess = { openResult ->
                                    if (generation != sessionGeneration) return@launch
                                    applyProviderConfig(openResult.asset)
                                    session = openResult.session
                                    subscribeToEvents(scope, generation, openResult.session)
                                },
                                onFailure = { error ->
                                    if (generation != sessionGeneration) return@launch
                                    _state.value =
                                        LiveVoiceUiState.Error(
                                            error.message ?: "Failed to start Live Voice",
                                        )
                                },
                            )
                        }
                }
            }
        }

        fun end(scope: CoroutineScope) {
            val finalUser = userCaption
            val finalAssistant = assistantCaption
            val persistedChatId = chatId
            val generation = sessionGeneration

            endJob =
                scope.launch(
                    errorHandler.coroutineExceptionHandler(TAG, "Failed to end Live Voice"),
                ) {
                    lifecycleMutex.withLock {
                        if (generation != sessionGeneration) return@withLock
                        try {
                            val resolvedChatId =
                                persistVoiceTurnUseCase(persistedChatId, finalUser, finalAssistant)
                            resolvedChatId?.let { chatId ->
                                if (persistedChatId == null) {
                                    onChatIdPersisted?.invoke(chatId)
                                }
                            }
                        } finally {
                            if (generation == sessionGeneration) {
                                teardown(resetToIdle = true)
                            }
                        }
                    }
                }
        }

        fun toggleMute() {
            isMuted = !isMuted
            updateActiveUiState()
        }

        fun cancel() {
            sessionGeneration += 1
            teardown(resetToIdle = true)
        }

        private fun applyProviderConfig(asset: LiveVoiceProviderAsset) {
            captureSampleRateHz = captureRateForProvider(asset.provider)
            playbackConfig = playbackConfigForProvider(asset.provider)
        }

        private fun subscribeToEvents(
            scope: CoroutineScope,
            generation: Int,
            liveSession: LiveVoiceSession,
        ) {
            eventsJob?.cancel()
            eventsJob =
                liveSession
                    .events()
                    .onEach { event ->
                        if (generation != sessionGeneration) return@onEach
                        // Handle events sequentially, in arrival order. Launching a coroutine per
                        // event lets audio chunks interleave and re-enter ensurePlayerInitialized(),
                        // which releases the AudioTrack mid-playback (no audio) and races caption
                        // state. Awaiting handleEvent here serializes the session event stream.
                        handleEvent(event)
                    }.launchIn(scope)
        }

        private suspend fun handleEvent(event: LiveVoiceEvent) {
            if (isTearingDown) return
            loggingPort.debug(TAG, "Live Voice event: ${event::class.simpleName}")
            when (event) {
                LiveVoiceEvent.Connected -> {
                    _state.value =
                        LiveVoiceUiState.Listening(
                            userCaption = userCaption,
                            assistantCaption = assistantCaption,
                            isMuted = isMuted,
                            micLevel = 0f,
                        )
                    startCapturePump()
                }
                is LiveVoiceEvent.UserTranscriptDelta -> {
                    userCaption += event.text
                    updateActiveUiState()
                }
                is LiveVoiceEvent.UserTranscriptDone -> {
                    userCaption = event.text
                    updateActiveUiState()
                }
                is LiveVoiceEvent.AssistantTranscriptDelta -> {
                    if (_state.value is LiveVoiceUiState.Listening) {
                        beginAssistantTurn()
                    }
                    assistantCaption += event.text
                    updateActiveUiState()
                }
                is LiveVoiceEvent.AssistantTranscriptDone -> {
                    assistantCaption = event.text
                    updateActiveUiState()
                }
                is LiveVoiceEvent.AssistantAudioChunk -> {
                    if (isTearingDown || suppressAssistantOutput) return
                    if (_state.value !is LiveVoiceUiState.AssistantSpeaking) {
                        beginAssistantTurn()
                    }
                    ensurePlayerInitialized()
                    if (!playbackStarted) {
                        streamingAudioPlayer.startPlayback()
                        playbackStarted = true
                    }
                    streamingAudioPlayer.enqueueChunk(event.pcm16)
                    _state.value =
                        LiveVoiceUiState.AssistantSpeaking(
                            userCaption = userCaption,
                            assistantCaption = assistantCaption,
                            isMuted = isMuted,
                        )
                }
                LiveVoiceEvent.SpeechStarted -> {
                    if (isInterruptibleAssistantOutput()) {
                        loggingPort.debug(TAG, "Server detected user speech during assistant playback")
                        triggerBargeIn()
                    }
                }
                is LiveVoiceEvent.Error -> {
                    if (isBenignLiveVoiceError(event.message)) {
                        loggingPort.debug(TAG, "Ignoring benign live voice error: ${event.message}")
                        return
                    }
                    _state.value = LiveVoiceUiState.Error(event.message)
                    teardown(resetToIdle = false)
                }
                LiveVoiceEvent.Closed -> {
                    teardown(resetToIdle = true)
                }
                LiveVoiceEvent.AssistantResponseComplete -> {
                    isAssistantResponseActive = false
                    persistCompletedTurnIfAny()
                    transitionToListeningAfterAssistant()
                }
                LiveVoiceEvent.SpeechStopped -> {
                    userBargeInActive = false
                    suppressAssistantOutput = false
                    bargeInHighEnergyFrameCount = 0
                    assistantPlaybackBaselineRms = 0f
                    transitionToListeningAfterAssistant()
                }
            }
        }

        private suspend fun ensurePlayerInitialized() {
            val config = liveVoicePlaybackConfig()
            if (!playerInitialized) {
                streamingAudioPlayer.initialize(config)
                playerInitialized = true
            }
        }

        private suspend fun reinitializePlayerForCaptureSession() {
            streamingAudioPlayer.stop()
            playerInitialized = false
            playbackStarted = false
            streamingAudioPlayer.initialize(liveVoicePlaybackConfig())
            playerInitialized = true
        }

        private fun liveVoicePlaybackConfig(): StreamingAudioConfig =
            playbackConfig.copy(
                audioSessionId = captureAudioSessionId,
                useVoiceCommunicationRoute = captureAudioSessionId != null,
            )

        private fun startCapturePump() {
            val scope = activeScope ?: return
            captureJob?.cancel()
            captureJob =
                scope.launch(
                    errorHandler.coroutineExceptionHandler(TAG, "Microphone capture failed"),
                ) {
                    realtimeAudioCapture
                        .captureEvents(captureSampleRateHz)
                        .catch { error ->
                            loggingPort.error(TAG, "Live Voice capture error: ${error.message}")
                            _state.value =
                                LiveVoiceUiState.Error(
                                    error.message ?: "Microphone capture failed",
                                )
                            teardown(resetToIdle = false)
                        }.collect { event ->
                            when (event) {
                                is RealtimeCaptureEvent.SessionStarted -> {
                                    captureAudioSessionId = event.info.audioSessionId
                                    isEchoCancellationActive = event.info.isEchoCancellationActive
                                    loggingPort.debug(
                                        TAG,
                                        "Capture session ready: aecActive=$isEchoCancellationActive " +
                                            "sessionId=${event.info.audioSessionId}",
                                    )
                                    reinitializePlayerForCaptureSession()
                                }
                                is RealtimeCaptureEvent.Pcm16Frame -> processCaptureFrame(event.bytes)
                            }
                        }
                }
        }

        private suspend fun processCaptureFrame(frame: ByteArray) {
            val micLevel = computeMicLevel(frame)
            latestMicLevel = micLevel

            if (isInterruptibleAssistantOutput() && !isMuted) {
                updateAssistantPlaybackBaseline(micLevel)
                if (detectLocalBargeIn(micLevel)) {
                    loggingPort.debug(
                        TAG,
                        "Local barge-in detected at mic level ${"%.3f".format(micLevel)}",
                    )
                    triggerBargeIn()
                }
            }

            updateActiveUiState()
            if (isMuted) return

            val uploadFrame =
                when (_state.value) {
                    is LiveVoiceUiState.Listening -> {
                        bargeInHighEnergyFrameCount = 0
                        RealtimeAudioCaptureGain.amplifyPcm16Le(frame, LISTENING_MIC_UPLOAD_GAIN)
                    }
                    is LiveVoiceUiState.AssistantSpeaking ->
                        RealtimeAudioCaptureGain.amplifyPcm16Le(frame, ASSISTANT_MIC_UPLOAD_GAIN)
                    else -> null
                }
            uploadFrame?.let { session?.sendAudio(it) }
        }

        private fun beginAssistantTurn() {
            isAssistantResponseActive = true
            userBargeInActive = false
            suppressAssistantOutput = false
            bargeInHighEnergyFrameCount = 0
            assistantPlaybackBaselineRms = 0f
            assistantCaption = ""
        }

        private fun updateAssistantPlaybackBaseline(micLevel: Float) {
            assistantPlaybackBaselineRms =
                assistantPlaybackBaselineRms * BASELINE_EMA_RETAIN + micLevel * BASELINE_EMA_ADOPT
        }

        private fun detectLocalBargeIn(micLevel: Float): Boolean {
            if (userBargeInActive) return false
            val threshold = bargeInEnergyThreshold()
            if (micLevel >= threshold) {
                bargeInHighEnergyFrameCount += 1
            } else {
                bargeInHighEnergyFrameCount = 0
            }
            return bargeInHighEnergyFrameCount >= BARGE_IN_CONSECUTIVE_FRAMES
        }

        private fun bargeInEnergyThreshold(): Float {
            if (!shouldUseFullDuplexCapture()) return BARGE_IN_RMS_THRESHOLD
            return maxOf(
                MIN_BARGE_IN_RMS_THRESHOLD,
                assistantPlaybackBaselineRms + BARGE_IN_BASELINE_MARGIN,
            )
        }

        private suspend fun triggerBargeIn() {
            if (userBargeInActive && suppressAssistantOutput) return
            if (userCaption.isNotBlank()) {
                persistCompletedTurnIfAny()
            }
            userBargeInActive = true
            suppressAssistantOutput = true
            if (isAssistantResponseActive) {
                session?.cancelAssistantResponse()
            }
            activateListeningAfterBargeIn()
        }

        private fun shouldUseFullDuplexCapture(): Boolean =
            isEchoCancellationActive && captureAudioSessionId != null

        private fun isInterruptibleAssistantOutput(): Boolean =
            _state.value is LiveVoiceUiState.AssistantSpeaking || playbackStarted

        private fun activateListeningAfterBargeIn() {
            streamingAudioPlayer.stop()
            playerInitialized = false
            playbackStarted = false
            bargeInHighEnergyFrameCount = 0
            assistantPlaybackBaselineRms = 0f
            _state.value =
                LiveVoiceUiState.Listening(
                    userCaption = userCaption,
                    assistantCaption = assistantCaption,
                    isMuted = isMuted,
                    micLevel = latestMicLevel,
                )
        }

        private fun transitionToListeningAfterAssistant() {
            if (_state.value !is LiveVoiceUiState.AssistantSpeaking &&
                _state.value !is LiveVoiceUiState.Listening
            ) {
                return
            }
            if (!userBargeInActive) {
                suppressAssistantOutput = false
                bargeInHighEnergyFrameCount = 0
                assistantPlaybackBaselineRms = 0f
            }
            _state.value =
                LiveVoiceUiState.Listening(
                    userCaption = userCaption,
                    assistantCaption = assistantCaption,
                    isMuted = isMuted,
                    micLevel = latestMicLevel,
                )
        }

        private var latestMicLevel: Float = 0f

        private fun computeMicLevel(frame: ByteArray): Float {
            if (frame.size < 2) return 0f
            var sum = 0.0
            var index = 0
            while (index < frame.size - 1) {
                val low = frame[index].toInt() and 0xFF
                val high = frame[index + 1].toInt() and 0xFF
                val sample = (high shl 8) or low
                val signed = if (sample > PCM16_SAMPLE_MAX) sample - PCM16_UNSIGNED_RANGE else sample
                sum += signed.toDouble() * signed.toDouble()
                index += 2
            }
            val sampleCount = frame.size / 2
            if (sampleCount == 0) return 0f
            val rms = sqrt(sum / sampleCount).toFloat() / PCM16_NORMALIZATION_SCALE
            return rms.coerceIn(0f, 1f)
        }

        private fun updateActiveUiState() {
            val current = _state.value
            _state.value =
                when (current) {
                    is LiveVoiceUiState.Listening ->
                        current.copy(
                            userCaption = userCaption,
                            assistantCaption = assistantCaption,
                            isMuted = isMuted,
                            micLevel = latestMicLevel,
                        )
                    is LiveVoiceUiState.AssistantSpeaking ->
                        current.copy(
                            userCaption = userCaption,
                            assistantCaption = assistantCaption,
                            isMuted = isMuted,
                        )
                    else -> current
                }
        }

        private fun stopJobsAndPlayback() {
            captureJob?.cancel()
            captureJob = null
            eventsJob?.cancel()
            eventsJob = null
            startJob?.cancel()
            startJob = null
            streamingAudioPlayer.stop()
            playerInitialized = false
            playbackStarted = false
            captureAudioSessionId = null
            isEchoCancellationActive = false
            userBargeInActive = false
            suppressAssistantOutput = false
            bargeInHighEnergyFrameCount = 0
            assistantPlaybackBaselineRms = 0f
            isAssistantResponseActive = false
        }

        private suspend fun persistCompletedTurnIfAny(
            userText: String = userCaption,
            assistantText: String = assistantCaption,
        ) {
            if (userText.isBlank() && assistantText.isBlank()) return
            val resolvedChatId = persistVoiceTurnUseCase(chatId, userText, assistantText)
            resolvedChatId?.let { resolved ->
                if (chatId == null) {
                    onChatIdPersisted?.invoke(resolved)
                }
                chatId = resolved
            }
            userCaption = ""
            assistantCaption = ""
        }

        private fun isBenignLiveVoiceError(message: String): Boolean {
            val normalized = message.lowercase()
            return normalized.contains("no active response") ||
                normalized.contains("cancellation failed")
        }

        private fun teardown(resetToIdle: Boolean) {
            isTearingDown = true
            val sessionToClose = session
            session = null
            stopJobsAndPlayback()
            if (sessionToClose != null) {
                activeScope?.launch {
                    runCatching { sessionToClose.close() }
                        .onFailure { error ->
                            loggingPort.error(TAG, "Failed to close Live Voice session: ${error.message}")
                        }
                }
            }
            if (resetToIdle) {
                _state.value = LiveVoiceUiState.Idle
            }
        }

        private companion object {
            private const val TAG = "LiveVoiceCoordinator"
            private const val CAPTURE_SAMPLE_RATE_OPENAI_HZ = 24_000
            private const val CAPTURE_SAMPLE_RATE_GOOGLE_HZ = 16_000
            private const val PCM16_SAMPLE_MAX = 32_767
            private const val PCM16_UNSIGNED_RANGE = 65_536
            private const val PCM16_NORMALIZATION_SCALE = 32_768f
            private const val LISTENING_MIC_UPLOAD_GAIN = 1.75f
            private const val ASSISTANT_MIC_UPLOAD_GAIN = 1f
            private const val BARGE_IN_RMS_THRESHOLD = 0.015f
            private const val MIN_BARGE_IN_RMS_THRESHOLD = 0.012f
            private const val BARGE_IN_BASELINE_MARGIN = 0.01f
            private const val BARGE_IN_CONSECUTIVE_FRAMES = 1
            private const val BASELINE_EMA_RETAIN = 0.95f
            private const val BASELINE_EMA_ADOPT = 0.05f

            private fun captureRateForProvider(provider: ApiProvider): Int =
                when (provider) {
                    ApiProvider.GOOGLE -> CAPTURE_SAMPLE_RATE_GOOGLE_HZ
                    ApiProvider.OPENAI,
                    ApiProvider.XAI,
                    -> CAPTURE_SAMPLE_RATE_OPENAI_HZ
                    else -> CAPTURE_SAMPLE_RATE_OPENAI_HZ
                }

            private fun playbackConfigForProvider(provider: ApiProvider): StreamingAudioConfig =
                when (provider) {
                    ApiProvider.GOOGLE -> StreamingAudioConfig.PCM_16_16KHZ_MONO
                    ApiProvider.OPENAI,
                    ApiProvider.XAI,
                    -> StreamingAudioConfig.PCM_16_24KHZ_MONO
                    else -> StreamingAudioConfig.PCM_16_24KHZ_MONO
                }
        }
    }
