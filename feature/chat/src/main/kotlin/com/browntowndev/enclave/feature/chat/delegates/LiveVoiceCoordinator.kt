package com.browntowndev.enclave.feature.chat.delegates

import androidx.annotation.VisibleForTesting
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureEvent
import com.browntowndev.enclave.domain.port.media.StreamingAudioConfig
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.port.media.VoiceChatAudioRoutePort
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionUseCase
import com.browntowndev.enclave.domain.usecase.chat.PersistVoiceTurnUseCase
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import com.browntowndev.enclave.feature.inference.media.RealtimeAudioCaptureGain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.min
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
        private val toolExecutorPort: ToolExecutorPort,
        private val voiceChatAudioRoute: VoiceChatAudioRoutePort,
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

        private var currentAsset: LiveVoiceProviderAsset? = null
        private val toolJobs = ConcurrentHashMap<String, Job>()
        private val acceptedToolCallIds = ConcurrentHashMap.newKeySet<String>()
        private var toolStatusCaption: String? = null

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
        private var turnPersisted: Boolean = false
        private var assistantTranscriptFinalized: Boolean = false
        private var responseCompleteAwaitingTranscript: Boolean = false
        private var assistantResponseCancelled: Boolean = false
        private val sessionEventRouter = SessionEventRouter()

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
                    turnPersisted = false
                    assistantTranscriptFinalized = false
                    responseCompleteAwaitingTranscript = false
                    assistantResponseCancelled = false
                    acceptedToolCallIds.clear()
                    latestMicLevel = 0f
                    resetAssistantAudioVisualization()
                    _state.value = LiveVoiceUiState.Connecting

                    startJob =
                        scope.launch(
                            errorHandler.coroutineExceptionHandler(TAG, "Failed to start Live Voice"),
                        ) {
                            liveVoiceSessionUseCase().fold(
                                onSuccess = { openResult ->
                                    if (generation != sessionGeneration) return@launch
                                    currentAsset = openResult.asset
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
            val generation = sessionGeneration

            endJob =
                scope.launch(
                    errorHandler.coroutineExceptionHandler(TAG, "Failed to end Live Voice"),
                ) {
                    lifecycleMutex.withLock {
                        if (generation != sessionGeneration) return@withLock
                        try {
                            persistCurrentTurnOnce(
                                userText = finalUser,
                                assistantText = finalAssistant,
                            )
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
                        sessionEventRouter.handle(event)
                    }.launchIn(scope)
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
            val micLevel = computePcmLevel(frame)
            latestMicLevel = micLevel

            if (_state.value is LiveVoiceUiState.AssistantSpeaking) {
                advanceAssistantAudioLevel()
            }

            if (isInterruptibleAssistantOutput() && !isMuted) {
                updateAssistantPlaybackBaseline(micLevel)
                if (detectLocalBargeIn(micLevel)) {
                    loggingPort.debug(
                        TAG,
                        "Local barge-in detected at mic level ${"%.3f".format(micLevel)}",
                    )
                    interruptAssistantOutput(persistPartialTurn = true)
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
            if (turnPersisted) return
            isAssistantResponseActive = true
            assistantTranscriptFinalized = false
            responseCompleteAwaitingTranscript = false
            userBargeInActive = false
            suppressAssistantOutput = false
            bargeInHighEnergyFrameCount = 0
            assistantPlaybackBaselineRms = 0f
            assistantCaption = ""
            resetAssistantAudioVisualization()
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

        private suspend fun interruptAssistantOutput(persistPartialTurn: Boolean) {
            if (userBargeInActive && suppressAssistantOutput) return
            toolJobs.values.forEach { it.cancel() }
            toolJobs.clear()
            toolStatusCaption = null
            if (persistPartialTurn && userCaption.isNotBlank()) {
                persistCurrentTurnOnce()
            }
            userBargeInActive = true
            suppressAssistantOutput = true
            if (isAssistantResponseActive) {
                assistantResponseCancelled = true
                session?.cancelAssistantResponse()
            }
            activateListeningAfterBargeIn()
        }

        private suspend fun finalizeAssistantTurnPersistence() {
            if (turnPersisted) return
            if (assistantTranscriptFinalized) {
                persistCurrentTurnOnce()
                return
            }
            responseCompleteAwaitingTranscript = true
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
        private var latestAssistantAudioLevel: Float = 0f
        private val assistantAudioLevelSamples = ArrayDeque<Float>()

        private fun enqueueAssistantAudioLevelSamples(pcm16: ByteArray) {
            var offset = 0
            while (offset < pcm16.size) {
                val endByte = min(offset + ASSISTANT_LEVEL_WINDOW_BYTES, pcm16.size)
                if (endByte - offset >= 2) {
                    assistantAudioLevelSamples.addLast(computePcmLevelRange(pcm16, offset, endByte))
                }
                offset += ASSISTANT_LEVEL_WINDOW_BYTES
            }
            while (assistantAudioLevelSamples.size > ASSISTANT_LEVEL_SAMPLE_QUEUE_MAX) {
                assistantAudioLevelSamples.removeFirst()
            }
        }

        private fun advanceAssistantAudioLevel() {
            val nextSample = assistantAudioLevelSamples.pollFirst()
            latestAssistantAudioLevel =
                if (nextSample != null) {
                    nextSample
                } else {
                    latestAssistantAudioLevel * ASSISTANT_AUDIO_LEVEL_DECAY
                }
        }

        private fun resetAssistantAudioVisualization() {
            assistantAudioLevelSamples.clear()
            latestAssistantAudioLevel = 0f
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
                            toolStatusCaption = toolStatusCaption,
                        )
                    is LiveVoiceUiState.AssistantSpeaking ->
                        current.copy(
                            userCaption = userCaption,
                            assistantCaption = assistantCaption,
                            isMuted = isMuted,
                            audioLevel = latestAssistantAudioLevel,
                            toolStatusCaption = toolStatusCaption,
                        )
                    else -> current
                }
        }

        private fun stopJobsAndPlayback() {
            toolJobs.values.forEach { it.cancel() }
            toolJobs.clear()
            acceptedToolCallIds.clear()
            toolStatusCaption = null
            voiceChatAudioRoute.deactivateSpeakerphone()
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
            turnPersisted = false
            assistantTranscriptFinalized = false
            responseCompleteAwaitingTranscript = false
            assistantResponseCancelled = false
            latestMicLevel = 0f
            resetAssistantAudioVisualization()
        }

        private suspend fun persistCurrentTurnOnce(
            userText: String = userCaption,
            assistantText: String = assistantCaption,
        ) {
            if (turnPersisted) return
            if (userText.isBlank() && assistantText.isBlank()) return
            turnPersisted = true
            assistantTranscriptFinalized = false
            responseCompleteAwaitingTranscript = false
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
            currentAsset = null
            stopJobsAndPlayback()
            if (sessionToClose != null) {
                sessionTeardownScope.launch {
                    withContext(NonCancellable) {
                        runCatching { sessionToClose.close() }
                            .onFailure { error ->
                                loggingPort.error(TAG, "Failed to close Live Voice session: ${error.message}")
                            }
                    }
                }
            }
            if (resetToIdle) {
                _state.value = LiveVoiceUiState.Idle
            }
        }

        private inner class ToolCallExecutor {
            fun handleRequested(event: LiveVoiceEvent.ToolCallRequested) {
                val scope = activeScope ?: return
                if (!acceptedToolCallIds.add(event.callId)) {
                    loggingPort.debug(
                        TAG,
                        "Ignoring duplicate Live Voice tool call id=${event.callId} name=${event.name}",
                    )
                    return
                }
                val generation = sessionGeneration
                toolJobs[event.callId]?.cancel()
                val job =
                    scope.launch {
                        val displayName = ToolDefinition.fromName(event.name)?.displayName
                        if (displayName != null) {
                            toolStatusCaption = displayName
                            updateActiveUiState()
                        }
                        try {
                            val result =
                                withContext(toolExecutionDispatcher) {
                                    toolExecutorPort.execute(
                                        ToolCallRequest(
                                            toolName = event.name,
                                            argumentsJson = event.argumentsJson,
                                            provider = currentAsset?.provider?.name ?: "UNKNOWN",
                                            modelType = ModelType.LIVE_VOICE,
                                            chatId = chatId,
                                        ),
                                    )
                                }
                            if (generation != sessionGeneration) return@launch
                            session?.sendToolResult(event.callId, event.name, result.resultJson)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            loggingPort.error(
                                TAG,
                                "Live Voice tool failed id=${event.callId} name=${event.name}: ${e.message}",
                                e,
                            )
                            if (generation != sessionGeneration) return@launch
                            val errorJson = buildToolErrorJson(e.message)
                            runCatching {
                                session?.sendToolResult(event.callId, event.name, errorJson)
                            }
                        } finally {
                            toolJobs.remove(event.callId, coroutineContext.job)
                            if (toolJobs.isEmpty()) {
                                toolStatusCaption = null
                                updateActiveUiState()
                            }
                        }
                    }
                toolJobs[event.callId] = job
            }

            private fun buildToolErrorJson(message: String?): String =
                buildJsonObject {
                    put("error", message ?: "tool execution failed")
                }.toString()
        }

        private val toolCallExecutor = ToolCallExecutor()

        private inner class SessionEventRouter {
            suspend fun handle(event: LiveVoiceEvent) {
                if (isTearingDown) return
                loggingPort.debug(TAG, "Live Voice event: ${event::class.simpleName}")
                when (event) {
                    LiveVoiceEvent.Connected -> handleConnected()
                    is LiveVoiceEvent.UserTranscriptDelta -> handleUserTranscriptDelta(event)
                    is LiveVoiceEvent.UserTranscriptDone -> handleUserTranscriptDone(event)
                    is LiveVoiceEvent.AssistantTranscriptDelta -> handleAssistantTranscriptDelta(event)
                    is LiveVoiceEvent.AssistantTranscriptDone -> handleAssistantTranscriptDone(event)
                    is LiveVoiceEvent.AssistantAudioChunk -> handleAssistantAudioChunk(event)
                    LiveVoiceEvent.SpeechStarted -> handleSpeechStarted()
                    is LiveVoiceEvent.Error -> handleError(event)
                    LiveVoiceEvent.Closed -> handleClosed()
                    LiveVoiceEvent.AssistantResponseComplete -> handleAssistantResponseComplete()
                    LiveVoiceEvent.SpeechStopped -> handleSpeechStopped()
                    is LiveVoiceEvent.ToolCallRequested ->
                        toolCallExecutor.handleRequested(event)
                }
            }

            private fun handleConnected() {
                voiceChatAudioRoute.activateSpeakerphone()
                _state.value =
                    LiveVoiceUiState.Listening(
                        userCaption = userCaption,
                        assistantCaption = assistantCaption,
                        isMuted = isMuted,
                        micLevel = 0f,
                    )
                startCapturePump()
            }

            private fun handleUserTranscriptDelta(event: LiveVoiceEvent.UserTranscriptDelta) {
                userCaption += event.text
                updateActiveUiState()
            }

            private fun handleUserTranscriptDone(event: LiveVoiceEvent.UserTranscriptDone) {
                if (turnPersisted) {
                    turnPersisted = false
                    assistantTranscriptFinalized = false
                    responseCompleteAwaitingTranscript = false
                }
                userCaption = event.text
                updateActiveUiState()
            }

            private fun handleAssistantTranscriptDelta(event: LiveVoiceEvent.AssistantTranscriptDelta) {
                if (shouldIgnoreAssistantTranscriptEvents() || turnPersisted) return
                if (_state.value is LiveVoiceUiState.Listening) {
                    beginAssistantTurn()
                }
                assistantCaption += event.text
                updateActiveUiState()
            }

            private suspend fun handleAssistantTranscriptDone(event: LiveVoiceEvent.AssistantTranscriptDone) {
                if (shouldIgnoreAssistantTranscriptEvents() || turnPersisted) return
                assistantCaption = event.text
                assistantTranscriptFinalized = true
                if (responseCompleteAwaitingTranscript) {
                    persistCurrentTurnOnce()
                    responseCompleteAwaitingTranscript = false
                    turnPersisted = false
                    assistantTranscriptFinalized = false
                }
                updateActiveUiState()
            }

            private suspend fun handleAssistantAudioChunk(event: LiveVoiceEvent.AssistantAudioChunk) {
                if (shouldIgnoreAssistantAudioChunk()) return
                if (_state.value !is LiveVoiceUiState.AssistantSpeaking) {
                    beginAssistantTurn()
                }
                ensurePlayerInitialized()
                if (!playbackStarted) {
                    streamingAudioPlayer.startPlayback()
                    playbackStarted = true
                }
                streamingAudioPlayer.enqueueChunk(event.pcm16)
                enqueueAssistantAudioLevelSamples(event.pcm16)
                advanceAssistantAudioLevel()
                _state.value =
                    LiveVoiceUiState.AssistantSpeaking(
                        userCaption = userCaption,
                        assistantCaption = assistantCaption,
                        isMuted = isMuted,
                        audioLevel = latestAssistantAudioLevel,
                        toolStatusCaption = toolStatusCaption,
                    )
            }

            private suspend fun handleSpeechStarted() {
                if (!isInterruptibleAssistantOutput()) return
                loggingPort.debug(TAG, "Server detected user speech during assistant playback")
                interruptAssistantOutput(persistPartialTurn = false)
            }

            private fun handleError(event: LiveVoiceEvent.Error) {
                if (isBenignLiveVoiceError(event.message)) {
                    loggingPort.debug(TAG, "Ignoring benign live voice error: ${event.message}")
                    return
                }
                _state.value = LiveVoiceUiState.Error(event.message)
                teardown(resetToIdle = false)
            }

            private fun handleClosed() {
                teardown(resetToIdle = true)
            }

            private suspend fun handleAssistantResponseComplete() {
                isAssistantResponseActive = false
                val wasAlreadyPersisted = turnPersisted
                finalizeAssistantTurnPersistence()
                transitionToListeningAfterAssistant()
                if (turnPersisted) {
                    turnPersisted = false
                    assistantTranscriptFinalized = false
                }
                if (!wasAlreadyPersisted) {
                    assistantResponseCancelled = false
                }
            }

            private fun handleSpeechStopped() {
                userBargeInActive = false
                suppressAssistantOutput = false
                assistantResponseCancelled = false
                bargeInHighEnergyFrameCount = 0
                assistantPlaybackBaselineRms = 0f
                transitionToListeningAfterAssistant()
            }

            private fun shouldIgnoreAssistantTranscriptEvents(): Boolean =
                assistantResponseCancelled || userBargeInActive || suppressAssistantOutput

            private fun shouldIgnoreAssistantAudioChunk(): Boolean =
                isTearingDown || assistantResponseCancelled || suppressAssistantOutput || turnPersisted
        }

        internal companion object {
            @VisibleForTesting
            @JvmStatic
            var sessionTeardownScopeOverride: CoroutineScope? = null

            @VisibleForTesting
            @JvmStatic
            var toolExecutionDispatcherOverride: CoroutineDispatcher? = null

            private val toolExecutionDispatcher: CoroutineDispatcher
                get() = toolExecutionDispatcherOverride ?: Dispatchers.IO

            private val sessionTeardownScope: CoroutineScope
                get() =
                    sessionTeardownScopeOverride
                        ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            private const val ASSISTANT_LEVEL_WINDOW_BYTES = 960
            private const val ASSISTANT_LEVEL_SAMPLE_QUEUE_MAX = 96
            private const val ASSISTANT_AUDIO_LEVEL_DECAY = 0.82f

            private fun computePcmLevel(frame: ByteArray): Float = computePcmLevelRange(frame, 0, frame.size)

            private fun computePcmLevelRange(
                frame: ByteArray,
                startByte: Int,
                endByte: Int,
            ): Float {
                if (endByte - startByte < 2) return 0f
                var sum = 0.0
                var index = startByte
                while (index < endByte - 1) {
                    val low = frame[index].toInt() and 0xFF
                    val high = frame[index + 1].toInt() and 0xFF
                    val sample = (high shl 8) or low
                    val signed = if (sample > PCM16_SAMPLE_MAX) sample - PCM16_UNSIGNED_RANGE else sample
                    sum += signed.toDouble() * signed.toDouble()
                    index += 2
                }
                val sampleCount = (endByte - startByte) / 2
                if (sampleCount == 0) return 0f
                val rms = sqrt(sum / sampleCount).toFloat() / PCM16_NORMALIZATION_SCALE
                return rms.coerceIn(0f, 1f)
            }

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
