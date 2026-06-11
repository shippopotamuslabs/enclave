package com.browntowndev.enclave.feature.chat.delegates

import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureEvent
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureSessionInfo
import com.browntowndev.enclave.domain.port.media.StreamingAudioConfig
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.port.media.VoiceChatAudioRoutePort
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionOpenResult
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionUseCase
import com.browntowndev.enclave.domain.usecase.chat.PersistVoiceTurnUseCase
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import com.browntowndev.enclave.feature.chat.ToolCallBannerKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LiveVoiceCoordinatorTest {
    private val liveVoiceSessionUseCase = mockk<LiveVoiceSessionUseCase>()
    private val persistVoiceTurnUseCase = mockk<PersistVoiceTurnUseCase>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutorPort = mockk<ToolExecutorPort>()
    private val voiceChatAudioRoute = mockk<VoiceChatAudioRoutePort>(relaxed = true)

    private val liveVoiceAsset =
        LiveVoiceProviderAsset(
            id = LiveVoiceProviderId("lv-1"),
            displayName = "OpenAI RT",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            modelName = null,
            baseUrl = null,
            credentialAlias = "alias",
        )

    private lateinit var fakeSession: FakeLiveVoiceSession
    private lateinit var fakeCapture: FakeRealtimeAudioCapture
    private lateinit var fakePlayer: FakeStreamingAudioPlayer
    private lateinit var coordinator: LiveVoiceCoordinator

    @BeforeEach
    fun setUp() {
        LiveVoiceCoordinator.sessionTeardownScopeOverride =
            CoroutineScope(UnconfinedTestDispatcher())
        LiveVoiceCoordinator.toolExecutionDispatcherOverride = UnconfinedTestDispatcher()

        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }

        fakeSession = FakeLiveVoiceSession()
        fakeCapture = FakeRealtimeAudioCapture()
        fakePlayer = FakeStreamingAudioPlayer()

        coordinator =
            LiveVoiceCoordinator(
                liveVoiceSessionUseCase = liveVoiceSessionUseCase,
                persistVoiceTurnUseCase = persistVoiceTurnUseCase,
                realtimeAudioCapture = fakeCapture,
                streamingAudioPlayer = fakePlayer,
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                toolExecutorPort = toolExecutorPort,
                voiceChatAudioRoute = voiceChatAudioRoute,
            )
    }

    @AfterEach
    fun tearDown() {
        LiveVoiceCoordinator.sessionTeardownScopeOverride = null
        LiveVoiceCoordinator.toolExecutionDispatcherOverride = null
    }

    private fun stubOpenSession() {
        coEvery { liveVoiceSessionUseCase() } returns
            Result.success(
                LiveVoiceSessionOpenResult(
                    session = fakeSession,
                    asset = liveVoiceAsset,
                ),
            )
    }

    private fun stubPersistVoiceTurn(
        chatId: ChatId?,
        userText: String,
        assistantText: String,
        resolvedChatId: ChatId = chatId ?: ChatId("c1"),
    ) {
        coEvery {
            persistVoiceTurnUseCase(
                chatId = chatId,
                userText = userText,
                assistantText = assistantText,
                webSearchSources = any(),
                assistantMessageId = any(),
            )
        } returns resolvedChatId
    }

    private fun verifyPersistVoiceTurn(
        chatId: ChatId?,
        userText: String,
        assistantText: String,
        exactly: Int = 1,
    ) {
        coVerify(exactly = exactly) {
            persistVoiceTurnUseCase(
                chatId = chatId,
                userText = userText,
                assistantText = assistantText,
                webSearchSources = any(),
                assistantMessageId = any(),
            )
        }
    }

    private fun verifyNoPersistVoiceTurn() {
        coVerify(exactly = 0) {
            persistVoiceTurnUseCase(
                chatId = any(),
                userText = any(),
                assistantText = any(),
                webSearchSources = any(),
                assistantMessageId = any(),
            )
        }
    }

    @Test
    @DisplayName("LV-COORD-001: start transitions Idle → Connecting → Listening")
    fun lvCoord001_connectThenListen() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.state.test {
                assertEquals(LiveVoiceUiState.Idle, awaitItem())

                coordinator.start(scope, chatId = null)
                assertEquals(LiveVoiceUiState.Connecting, awaitItem())

                fakeSession.emit(LiveVoiceEvent.Connected)
                advanceUntilIdle()

                val listening = awaitItem() as LiveVoiceUiState.Listening
                assertFalse(listening.isMuted)
            }

            verify(exactly = 1) { voiceChatAudioRoute.activateSpeakerphone() }
        }

    @Test
    @DisplayName("LV-COORD-002: assistant audio chunk enqueues playback and sets AssistantSpeaking")
    fun lvCoord002_assistantAudioChunk() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(9, 9)))
            advanceUntilIdle()

            assertEquals(1, fakePlayer.enqueuedChunks.size)
            assertArrayEquals(byteArrayOf(9, 9), fakePlayer.enqueuedChunks.single())
            assertTrue(fakePlayer.playbackStarted)
            assertTrue(coordinator.state.value is LiveVoiceUiState.AssistantSpeaking)
        }

    @Test
    @DisplayName("LV-COORD-003: server SpeechStarted during assistant speech triggers barge-in")
    fun lvCoord003_serverSpeechStartedTriggersBargeIn() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.SpeechStarted)
            advanceUntilIdle()

            assertTrue(fakePlayer.stopped)
            assertEquals(1, fakeSession.cancelAssistantResponseCount)
            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)
        }

    @Test
    @DisplayName("LV-COORD-003b: full-duplex SpeechStarted cancels assistant playback")
    fun lvCoord003b_fullDuplexSpeechStartedStopsPlayback() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.SpeechStarted)
            advanceUntilIdle()

            assertTrue(fakePlayer.stopped)
            assertEquals(1, fakeSession.cancelAssistantResponseCount)
            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)
        }

    @Test
    @DisplayName("LV-COORD-003c: local barge-in during assistant speech stops playback and sends audio")
    fun lvCoord003c_localBargeInStopsPlayback() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            assertTrue(fakeSession.sentAudio.isEmpty())

            emitLoudBargeInFrames(fakeCapture)
            advanceUntilIdle()

            assertTrue(fakePlayer.stopped)
            assertEquals(1, fakeSession.cancelAssistantResponseCount)
            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)
            assertTrue(fakeSession.sentAudio.isNotEmpty())
        }

    @Test
    @DisplayName("LV-COORD-004: mute pauses capture so no frames are sent")
    fun lvCoord004_mutePausesCapture() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            advanceUntilIdle()

            coordinator.toggleMute()
            fakeCapture.emit(byteArrayOf(7))
            advanceUntilIdle()

            val listening = coordinator.state.value as LiveVoiceUiState.Listening
            assertTrue(listening.isMuted)
            assertTrue(fakeSession.sentAudio.isEmpty())
        }

    @Test
    @DisplayName("LV-COORD-005: end persists accumulated transcript turns")
    fun lvCoord005_endPersistsTurns() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(chatId = ChatId("c1"), userText = "hello", assistantText = "hi")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hello"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("hi"))
            advanceUntilIdle()

            coordinator.end(scope)
            advanceUntilIdle()

            verifyPersistVoiceTurn(chatId = ChatId("c1"), userText = "hello", assistantText = "hi")
            assertEquals(LiveVoiceUiState.Idle, coordinator.state.value)
        }

    @Test
    @DisplayName("LV-COORD-006: session-open failure surfaces as Error state")
    fun lvCoord006_sessionOpenFailure() =
        runTest {
            coEvery { liveVoiceSessionUseCase() } returns
                Result.failure(IllegalStateException("No Live Voice provider assigned."))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.state.test {
                skipItems(1)
                coordinator.start(scope, chatId = null)
                assertEquals(LiveVoiceUiState.Connecting, awaitItem())
                assertEquals(
                    LiveVoiceUiState.Error("No Live Voice provider assigned."),
                    awaitItem(),
                )
            }

            assertEquals(0, fakeCapture.captureEventsCallCount)
        }

    @Test
    @DisplayName("LV-COORD-007: cancel closes session without persisting")
    fun lvCoord007_cancelDoesNotPersist() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hi"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("hello"))
            advanceUntilIdle()

            coordinator.cancel()
            advanceUntilIdle()

            verifyNoPersistVoiceTurn()
            assertTrue(fakeSession.closed)
            assertEquals(LiveVoiceUiState.Idle, coordinator.state.value)
        }

    @Test
    @DisplayName("LV-COORD-008: Delta appends caption, Done replaces caption")
    fun lvCoord008_deltaAppendsDoneReplaces() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.UserTranscriptDelta("turn on "))
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDelta("the lights"))
            advanceUntilIdle()

            val afterDeltas = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals("turn on the lights", afterDeltas.userCaption)

            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("turn on the lights, please"))
            advanceUntilIdle()

            val afterDone = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals("turn on the lights, please", afterDone.userCaption)
        }

    @Test
    @DisplayName("LV-COORD-009: half-duplex pauses mic upload during assistant speech without AEC")
    fun lvCoord009_halfDuplexPausesMicUploadDuringAssistantSpeech() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            fakeCapture.emit(quietPcm16Frame())
            advanceUntilIdle()

            assertTrue(fakeSession.sentAudio.isEmpty())
        }

    @Test
    @DisplayName("LV-COORD-009b: full-duplex uploads mic during assistant speech when AEC is active")
    fun lvCoord009b_fullDuplexUploadsMicDuringAssistantSpeech() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            fakeCapture.emit(quietPcm16Frame())
            advanceUntilIdle()

            assertTrue(fakeSession.sentAudio.isNotEmpty())
        }

    @Test
    @DisplayName("LV-COORD-010: player re-inits with capture session id for echo cancellation")
    fun lvCoord010_playerUsesCaptureSessionId() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(audioSessionId = 42, echoCancellationActive = true)
            advanceUntilIdle()

            assertEquals(42, fakePlayer.lastInitializedConfig?.audioSessionId)
            assertEquals(true, fakePlayer.lastInitializedConfig?.useVoiceCommunicationRoute)
        }

    @Test
    @DisplayName("LV-COORD-011: stale assistant chunks suppressed after barge-in until SpeechStopped")
    fun lvCoord011_staleAssistantChunksSuppressedAfterBargeIn() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            emitLoudBargeInFrames(fakeCapture)
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(2)))
            advanceUntilIdle()

            assertEquals(1, fakePlayer.enqueuedChunks.size)
            assertFalse(fakePlayer.playbackStarted)

            fakeSession.emit(LiveVoiceEvent.SpeechStopped)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(3)))
            advanceUntilIdle()

            assertEquals(2, fakePlayer.enqueuedChunks.size)
            assertTrue(fakePlayer.playbackStarted)
        }

    @Test
    @DisplayName("LV-COORD-013: end during assistant playback tears down without enqueueing")
    fun lvCoord013_endDuringAssistantPlayback() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(chatId = null, userText = "", assistantText = "")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1, 2, 3, 4)))
            advanceUntilIdle()

            coordinator.end(scope)
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(5, 6, 7, 8)))
            advanceUntilIdle()

            assertEquals(LiveVoiceUiState.Idle, coordinator.state.value)
            assertEquals(1, fakePlayer.enqueuedChunks.size)
            assertTrue(fakePlayer.stopped)
            assertTrue(fakeSession.closed)
        }

    @Test
    @DisplayName("LV-COORD-014: end while enqueueChunk is in-flight stops player safely")
    fun lvCoord014_endWhileEnqueueInFlight() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(chatId = null, userText = "", assistantText = "partial")
            val slowPlayer = SlowFakeStreamingAudioPlayer()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator =
                LiveVoiceCoordinator(
                    liveVoiceSessionUseCase = liveVoiceSessionUseCase,
                    persistVoiceTurnUseCase = persistVoiceTurnUseCase,
                    realtimeAudioCapture = fakeCapture,
                    streamingAudioPlayer = slowPlayer,
                    errorHandler = errorHandler,
                    loggingPort = loggingPort,
                    toolExecutorPort = toolExecutorPort,
                    voiceChatAudioRoute = voiceChatAudioRoute,
                )

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("partial"))
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1, 2, 3, 4)))
            slowPlayer.awaitEnqueueEntered()
            assertEquals(1, slowPlayer.enqueuedChunks.size)

            coordinator.end(scope)
            slowPlayer.releaseBlockedEnqueue()
            advanceUntilIdle()

            assertEquals(LiveVoiceUiState.Idle, coordinator.state.value)
            assertTrue(slowPlayer.stopped)
            assertEquals(1, slowPlayer.enqueuedChunks.size)
            assertTrue(fakeSession.closed)
        }

    @Test
    @DisplayName("LV-COORD-015: cancel during assistant playback tears down without late side effects")
    fun lvCoord015_cancelDuringAssistantPlayback() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(9)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("still talking"))
            advanceUntilIdle()

            coordinator.cancel()
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(8)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("more"))
            advanceUntilIdle()

            assertEquals(LiveVoiceUiState.Idle, coordinator.state.value)
            assertEquals(1, fakePlayer.enqueuedChunks.size)
            assertTrue(fakePlayer.stopped)
            verifyNoPersistVoiceTurn()
        }

    @Test
    @DisplayName("LV-COORD-016: assistant response complete persists turn and returns to Listening")
    fun lvCoord016_assistantResponseCompleteReturnsToListening() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(chatId = null, userText = "", assistantText = "Hi there")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("Hi there"))
            advanceUntilIdle()
            assertTrue(coordinator.state.value is LiveVoiceUiState.AssistantSpeaking)

            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)
            val listening = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals("", listening.assistantCaption)
            verifyPersistVoiceTurn(chatId = null, userText = "", assistantText = "Hi there")
        }

    @Test
    @DisplayName("LV-COORD-018: benign cancellation error does not tear down session")
    fun lvCoord018_benignCancellationErrorIgnored() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            fakeSession.emit(
                LiveVoiceEvent.Error("Cancellation failed: no active response found"),
            )
            advanceUntilIdle()

            assertTrue(coordinator.state.value is LiveVoiceUiState.AssistantSpeaking)
            assertFalse(fakeSession.closed)
        }

    @Test
    @DisplayName("LV-COORD-019: tail playback interrupt skips cancel when response already completed")
    fun lvCoord019_tailPlaybackInterruptSkipsCancel() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            assertTrue(fakePlayer.playbackStarted)
            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)

            emitLoudBargeInFrames(fakeCapture)
            advanceUntilIdle()

            assertEquals(0, fakeSession.cancelAssistantResponseCount)
            assertTrue(fakePlayer.stopped)
            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)
        }

    @Test
    @DisplayName("LV-COORD-017: half-duplex resumes mic upload after assistant response completes")
    fun lvCoord017_halfDuplexResumesMicAfterAssistantComplete() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            advanceUntilIdle()

            fakeCapture.emit(quietPcm16Frame())
            advanceUntilIdle()
            assertTrue(fakeSession.sentAudio.isEmpty())

            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            fakeCapture.emit(quietPcm16Frame())
            advanceUntilIdle()

            assertTrue(fakeSession.sentAudio.isNotEmpty())
        }

    @Test
    @DisplayName("LV-COORD-012: assistant caption resets between turns")
    fun lvCoord012_assistantCaptionResetsBetweenTurns() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("Hello there"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            val afterUserTurn = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals("", afterUserTurn.assistantCaption)

            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("Hi again"))
            advanceUntilIdle()

            val nextTurn = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals("Hi again", nextTurn.assistantCaption)
        }

    @Test
    @DisplayName("LV-COORD-022: cumulative user transcript updates do not chunk into multiple messages")
    fun lvCoord022_cumulativeUserTranscriptDoesNotChunkPersist() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("I want you to"))
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("I want you to take on the persona"))
            fakeSession.emit(
                LiveVoiceEvent.UserTranscriptDone(
                    "I want you to take on the persona of a talking, yelling at their deadbeat boyfriend.",
                ),
            )
            advanceUntilIdle()

            verifyNoPersistVoiceTurn()

            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("Persona engaged."))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "I want you to take on the persona of a talking, yelling at their deadbeat boyfriend.",
                assistantText = "Persona engaged.",
            )
        }

    @Test
    @DisplayName("LV-COORD-021: local barge-in persists user turn and partial assistant transcript")
    fun lvCoord021_localBargeInPersistsPartialAssistantTurn() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "clean the fish",
                assistantText = "How to clean a fish",
            )
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("clean the fish"))
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("How to clean a fish"))
            advanceUntilIdle()

            emitLoudBargeInFrames(fakeCapture)
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "clean the fish",
                assistantText = "How to clean a fish",
            )
            assertTrue(coordinator.state.value is LiveVoiceUiState.Listening)
        }

    @Test
    @DisplayName("LV-COORD-023: server SpeechStarted alone does not persist a partial assistant turn")
    fun lvCoord023_serverSpeechStartedDoesNotPersistPartialTurn() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("tell me a story"))
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("Once upon a time"))
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.SpeechStarted)
            advanceUntilIdle()

            verifyNoPersistVoiceTurn()
            assertTrue(fakePlayer.stopped)
        }

    @Test
    @DisplayName("LV-COORD-024: duplicate AssistantResponseComplete persists exactly one turn")
    fun lvCoord024_duplicateAssistantResponseCompletePersistsOnce() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "hello",
                assistantText = "full assistant reply",
            )
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hello"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("full assistant reply"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "hello",
                assistantText = "full assistant reply",
            )
        }

    @Test
    @DisplayName("LV-COORD-026: late assistant events after barge-in persist do not create a second message")
    fun lvCoord026_lateEventsAfterBargeInPersistDoNotSplitTurn() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "clean the fish",
                assistantText = "How to clean a fish",
            )
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("clean the fish"))
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("How to clean a fish"))
            advanceUntilIdle()

            emitLoudBargeInFrames(fakeCapture)
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(2)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("How to clean a fish completely"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "clean the fish",
                assistantText = "How to clean a fish",
            )
        }

    @Test
    @DisplayName("LV-COORD-027: assistant audio levels vary as chunk windows are drained")
    fun lvCoord027_assistantAudioLevelsVaryAcrossChunkWindows() =
        runTest {
            stubOpenSession()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = null)
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(mixedAssistantAudioChunk()))
            advanceUntilIdle()

            val initialLevel = (coordinator.state.value as LiveVoiceUiState.AssistantSpeaking).audioLevel
            assertTrue(initialLevel > 0.1f)

            fakeCapture.emit(quietPcm16Frame())
            advanceUntilIdle()

            val drainedLevel = (coordinator.state.value as LiveVoiceUiState.AssistantSpeaking).audioLevel
            assertTrue(drainedLevel < initialLevel)
        }

    @Test
    @DisplayName("LV-COORD-025: response complete waits for final assistant transcript before persisting")
    fun lvCoord025_responseCompleteWaitsForFinalTranscript() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "hello",
                assistantText = "final transcript",
            )
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hello"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("final"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            verifyNoPersistVoiceTurn()

            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("final transcript"))
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "hello",
                assistantText = "final transcript",
            )
        }

    @Test
    @DisplayName("LV-COORD-020: each completed assistant response persists its turn immediately")
    fun lvCoord020_persistsEachCompletedTurn() =
        runTest {
            stubOpenSession()
            stubPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "first question",
                assistantText = "first answer",
            )
            stubPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "second question",
                assistantText = "second answer",
            )
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("first question"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("first answer"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("second question"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("second answer"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "first question",
                assistantText = "first answer",
            )
            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "second question",
                assistantText = "second answer",
            )
        }

    private suspend fun TestScope.startActiveCoordinator(chatId: ChatId = ChatId("c1")): CoroutineScope {
        stubOpenSession()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        coordinator.start(scope, chatId)
        fakeSession.emit(LiveVoiceEvent.Connected)
        fakeCapture.emitSession(echoCancellationActive = true)
        advanceUntilIdle()
        return scope
    }

    private suspend fun TestScope.suspendToolExecutor(
        result: ToolExecutionResult = ToolExecutionResult(toolName = "web_search", resultJson = """{"results":[]}"""),
    ): Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>> {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        coEvery { toolExecutorPort.execute(any()) } coAnswers {
            started.complete(Unit)
            gate.await()
            result
        }
        return started to gate
    }

    @Test
    @DisplayName("LV-TOOL-067")
    fun lvTool067_duplicateToolCallIdExecutesOnce() =
        runTest {
            coEvery { toolExecutorPort.execute(any()) } returns
                ToolExecutionResult(toolName = "web_search", resultJson = """{"results":[]}""")
            startActiveCoordinator()

            val duplicateEvent =
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_dup",
                    name = "web_search",
                    argumentsJson = """{"query":"weather"}""",
                )
            fakeSession.emit(duplicateEvent)
            fakeSession.emit(duplicateEvent)
            advanceUntilIdle()

            coVerify(exactly = 1) { toolExecutorPort.execute(any()) }
            assertEquals(1, fakeSession.sendToolResultInvocations.size)
            val listening = coordinator.state.value as LiveVoiceUiState.Listening
            assertNull(listening.activeToolCallBanner)
        }

    @Test
    @DisplayName("LV-TOOL-060")
    fun lvTool060_happyPathToolExecution() =
        runTest {
            coEvery {
                toolExecutorPort.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"test"}""",
                        provider = "OPENAI",
                        modelType = ModelType.LIVE_VOICE,
                        chatId = ChatId("c1"),
                    ),
                )
            } returns ToolExecutionResult(toolName = "web_search", resultJson = """{"results":[]}""")
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_1",
                    name = "web_search",
                    argumentsJson = """{"query":"test"}""",
                ),
            )
            advanceUntilIdle()

            coVerify(exactly = 1) {
                toolExecutorPort.execute(
                    ToolCallRequest(
                        toolName = "web_search",
                        argumentsJson = """{"query":"test"}""",
                        provider = "OPENAI",
                        modelType = ModelType.LIVE_VOICE,
                        chatId = ChatId("c1"),
                    ),
                )
            }
            assertEquals(1, fakeSession.sendToolResultInvocations.size)
            assertEquals(
                Triple("call_1", "web_search", """{"results":[]}"""),
                fakeSession.sendToolResultInvocations.single(),
            )
        }

    @Test
    @DisplayName("LV-TOOL-061")
    fun lvTool061_failureSendsErrorJsonStaysInSession() =
        runTest {
            coEvery { toolExecutorPort.execute(any()) } throws RuntimeException("network down")
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_2",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            advanceUntilIdle()

            assertEquals(1, fakeSession.sendToolResultInvocations.size)
            val resultJson = fakeSession.sendToolResultInvocations.single().third
            assertTrue(resultJson.contains("error"))
            assertTrue(resultJson.contains("network down"))
            assertFalse(coordinator.state.value is LiveVoiceUiState.Error)
            assertTrue(
                coordinator.state.value is LiveVoiceUiState.Listening ||
                    coordinator.state.value is LiveVoiceUiState.AssistantSpeaking,
            )
        }

    @Test
    @DisplayName("LV-TOOL-062")
    fun lvTool062_bargeInCancelsInFlightToolJob() =
        runTest {
            val (started, _) = suspendToolExecutor()
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_3",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            started.await()
            fakeSession.emit(LiveVoiceEvent.SpeechStarted)
            advanceUntilIdle()

            assertTrue(fakeSession.sendToolResultInvocations.isEmpty())
        }

    @Test
    @DisplayName("LV-TOOL-063")
    fun lvTool063_teardownCancelsInFlightToolJob() =
        runTest {
            val (started, _) = suspendToolExecutor()
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_4",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            started.await()
            coordinator.cancel()
            advanceUntilIdle()

            assertTrue(fakeSession.sendToolResultInvocations.isEmpty())
        }

    @Test
    @DisplayName("LV-TOOL-064")
    fun lvTool064_endCancelsInFlightToolJob() =
        runTest {
            val (started, _) = suspendToolExecutor()
            val scope = startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_5",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            started.await()
            coordinator.end(scope)
            advanceUntilIdle()

            assertTrue(fakeSession.sendToolResultInvocations.isEmpty())
        }

    @Test
    @DisplayName("LV-TOOL-065")
    fun lvTool065_unknownToolShowsGenericBanner() =
        runTest {
            val (started, gate) =
                suspendToolExecutor(
                    ToolExecutionResult(toolName = "unknown_tool", resultJson = "{}"),
                )
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_u",
                    name = "unknown_tool",
                    argumentsJson = "{}",
                ),
            )
            started.await()
            advanceUntilIdle()

            val listening = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals(ToolCallBannerKind.GENERIC, listening.activeToolCallBanner?.kind)
            assertEquals("Executing tool", listening.activeToolCallBanner?.label)

            gate.complete(Unit)
            advanceUntilIdle()

            val cleared = coordinator.state.value as LiveVoiceUiState.Listening
            assertNull(cleared.activeToolCallBanner)
        }

    @Test
    @DisplayName("LV-TOOL-024")
    fun lvTool024_staleSessionGenerationSkipsSendToolResult() =
        runTest {
            val (started, gate) = suspendToolExecutor(
                ToolExecutionResult(toolName = "web_search", resultJson = """{"ok":true}"""),
            )
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_6",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            started.await()
            coordinator.cancel()
            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(fakeSession.sendToolResultInvocations.isEmpty())
        }

    @Test
    @DisplayName("LV-TOOL-066")
    fun lvTool066_toolWithoutDisplayNameUsesFallbackBannerLabel() =
        runTest {
            assertNull(ToolDefinition.MANAGE_MEMORIES.displayName)
            val (started, gate) =
                suspendToolExecutor(
                    ToolExecutionResult(toolName = "manage_memories", resultJson = "{}"),
                )
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_mm",
                    name = "manage_memories",
                    argumentsJson = """{"action":"search"}""",
                ),
            )
            started.await()
            val listening = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals("Executing tool", listening.activeToolCallBanner?.label)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, fakeSession.sendToolResultInvocations.size)
            val finalListening = coordinator.state.value as LiveVoiceUiState.Listening
            assertNull(finalListening.activeToolCallBanner)
        }

    @Test
    @DisplayName("LV-TOOL-070")
    fun lvTool070_activeToolCallBannerLifecycle() =
        runTest {
            assertEquals("Searching the web...", ToolDefinition.WEB_SEARCH.displayName)
            val (started, gate) = suspendToolExecutor()
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_1",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            started.await()
            val listening = coordinator.state.value as LiveVoiceUiState.Listening
            assertEquals(ToolCallBannerKind.SEARCH, listening.activeToolCallBanner?.kind)
            assertEquals("Searching the web...", listening.activeToolCallBanner?.label)

            gate.complete(Unit)
            advanceUntilIdle()

            val cleared = coordinator.state.value as LiveVoiceUiState.Listening
            assertNull(cleared.activeToolCallBanner)
        }

    @Test
    @DisplayName("LV-TOOL-072")
    fun lvTool072_searchToolSourcesPersistedWithVoiceTurn() =
        runTest {
            val searchResultJson =
                """
                {
                  "results": [
                    {
                      "title": "Example",
                      "url": "https://example.com",
                      "content": "Example content",
                      "score": 0.95
                    }
                  ]
                }
                """.trimIndent()
            val sourcesSlot = slot<List<com.browntowndev.enclave.domain.model.chat.WebSearchSource>>()
            coEvery { toolExecutorPort.execute(any()) } returns
                ToolExecutionResult(toolName = "web_search", resultJson = searchResultJson)
            coEvery {
                persistVoiceTurnUseCase(
                    chatId = any(),
                    userText = any(),
                    assistantText = any(),
                    webSearchSources = capture(sourcesSlot),
                    assistantMessageId = any(),
                )
            } returns ChatId("c1")
            val scope = startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_sources",
                    name = "web_search",
                    argumentsJson = """{"query":"latest news"}""",
                ),
            )
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("latest news"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("here is what I found"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            verifyPersistVoiceTurn(
                chatId = ChatId("c1"),
                userText = "latest news",
                assistantText = "here is what I found",
            )
            assertEquals(1, sourcesSlot.captured.size)
            assertEquals("https://example.com", sourcesSlot.captured.single().url)
            assertEquals("Example", sourcesSlot.captured.single().title)
            coordinator.end(scope)
            advanceUntilIdle()
        }

    @Test
    @DisplayName("LV-TOOL-MUT-007")
    fun lvToolMut007_toolFailureMustNotSetSessionError() =
        runTest {
            coEvery { toolExecutorPort.execute(any()) } throws IllegalStateException("executor broken")
            startActiveCoordinator()

            fakeSession.emit(
                LiveVoiceEvent.ToolCallRequested(
                    callId = "call_7",
                    name = "web_search",
                    argumentsJson = "{}",
                ),
            )
            advanceUntilIdle()

            assertFalse(
                coordinator.state.value is LiveVoiceUiState.Error &&
                    (coordinator.state.value as LiveVoiceUiState.Error).message == "executor broken",
            )
            assertEquals(1, fakeSession.sendToolResultInvocations.size)
            assertTrue(fakeSession.sendToolResultInvocations.single().third.contains("error"))
        }

    private class FakeLiveVoiceSession : LiveVoiceSession {
        private val eventsFlow = MutableSharedFlow<LiveVoiceEvent>(extraBufferCapacity = 16)
        val sentAudio = mutableListOf<ByteArray>()
        val sendToolResultInvocations = mutableListOf<Triple<String, String, String>>()
        var closed = false
        var cancelAssistantResponseCount = 0

        suspend fun emit(event: LiveVoiceEvent) {
            eventsFlow.emit(event)
        }

        override fun events(): Flow<LiveVoiceEvent> = eventsFlow.asSharedFlow()

        override suspend fun sendAudio(pcm16: ByteArray) {
            sentAudio.add(pcm16)
        }

        override suspend fun commitTurn() = Unit

        override suspend fun cancelAssistantResponse() {
            cancelAssistantResponseCount += 1
        }

        override suspend fun close() {
            closed = true
        }

        override suspend fun sendToolResult(
            callId: String,
            name: String,
            resultJson: String,
        ) {
            sendToolResultInvocations.add(Triple(callId, name, resultJson))
        }
    }

    private class FakeRealtimeAudioCapture : RealtimeAudioCapturePort {
        private val events = MutableSharedFlow<RealtimeCaptureEvent>(extraBufferCapacity = 32)
        var captureEventsCallCount = 0

        override fun captureEvents(sampleRate: Int): Flow<RealtimeCaptureEvent> {
            captureEventsCallCount += 1
            return events.asSharedFlow()
        }

        suspend fun emitSession(
            audioSessionId: Int = 1,
            echoCancellationActive: Boolean,
        ) {
            events.emit(
                RealtimeCaptureEvent.SessionStarted(
                    RealtimeCaptureSessionInfo(
                        audioSessionId = audioSessionId,
                        isEchoCancellationActive = echoCancellationActive,
                    ),
                ),
            )
        }

        suspend fun emit(frame: ByteArray) {
            events.emit(RealtimeCaptureEvent.Pcm16Frame(frame))
        }
    }

    private open class FakeStreamingAudioPlayer : StreamingAudioPlayerPort {
        val enqueuedChunks = mutableListOf<ByteArray>()
        var stopped = false
        var playbackStarted = false
        var lastInitializedConfig: StreamingAudioConfig? = null

        override suspend fun initialize(config: StreamingAudioConfig) {
            lastInitializedConfig = config
            stopped = false
        }

        override suspend fun enqueueChunk(audioChunk: ByteArray) {
            enqueuedChunks.add(audioChunk)
        }

        override fun startPlayback() {
            playbackStarted = true
        }

        override fun stop() {
            stopped = true
            playbackStarted = false
        }

        override fun isInitialized(): Boolean = true
    }

    private class SlowFakeStreamingAudioPlayer : FakeStreamingAudioPlayer() {
        private val enqueueEntered = CompletableDeferred<Unit>()
        private val releaseEnqueue = CompletableDeferred<Unit>()

        override suspend fun enqueueChunk(audioChunk: ByteArray) {
            enqueuedChunks.add(audioChunk)
            enqueueEntered.complete(Unit)
            releaseEnqueue.await()
        }

        suspend fun awaitEnqueueEntered() {
            enqueueEntered.await()
        }

        fun releaseBlockedEnqueue() {
            releaseEnqueue.complete(Unit)
        }
    }

    private companion object {
        private const val LOUD_SAMPLE = 12_000
        private const val QUIET_SAMPLE = 100

        fun mixedAssistantAudioChunk(): ByteArray {
            val loud = loudPcm16Frame()
            val quiet = quietPcm16Frame()
            val chunk = ByteArray(1920)
            for (index in chunk.indices) {
                chunk[index] = if (index < 960) loud[index % loud.size] else quiet[index % quiet.size]
            }
            return chunk
        }

        fun loudPcm16Frame(): ByteArray {
            val low = (LOUD_SAMPLE and 0xFF).toByte()
            val high = ((LOUD_SAMPLE shr 8) and 0xFF).toByte()
            return byteArrayOf(low, high, low, high)
        }

        suspend fun emitLoudBargeInFrames(
            fakeCapture: FakeRealtimeAudioCapture,
            frameCount: Int = 3,
        ) {
            repeat(frameCount) {
                fakeCapture.emit(loudPcm16Frame())
            }
        }

        fun quietPcm16Frame(): ByteArray {
            val low = (QUIET_SAMPLE and 0xFF).toByte()
            val high = ((QUIET_SAMPLE shr 8) and 0xFF).toByte()
            return byteArrayOf(low, high, low, high)
        }
    }
}