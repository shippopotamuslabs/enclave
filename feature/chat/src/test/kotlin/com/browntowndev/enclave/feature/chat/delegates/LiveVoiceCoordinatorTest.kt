package com.browntowndev.enclave.feature.chat.delegates

import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureEvent
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureSessionInfo
import com.browntowndev.enclave.domain.port.media.StreamingAudioConfig
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionOpenResult
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionUseCase
import com.browntowndev.enclave.domain.usecase.chat.PersistVoiceTurnUseCase
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
            )
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

            fakeCapture.emit(loudPcm16Frame())
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
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "hello", "hi")
            } returns ChatId("c1")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hello"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("hi"))
            advanceUntilIdle()

            coordinator.end(scope)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "hello", "hi")
            }
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

            coVerify(exactly = 0) { persistVoiceTurnUseCase(any(), any(), any()) }
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
    @DisplayName("LV-COORD-009: mic upload continues during assistant speech for server-side interrupt")
    fun lvCoord009_micUploadContinuesDuringAssistantSpeech() =
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

            repeat(2) {
                fakeCapture.emit(loudPcm16Frame())
            }
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
            coEvery {
                persistVoiceTurnUseCase(null, "", "")
            } returns ChatId("c1")
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
            coEvery {
                persistVoiceTurnUseCase(null, "", "partial")
            } returns ChatId("c1")
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
            coVerify(exactly = 0) { persistVoiceTurnUseCase(any(), any(), any()) }
        }

    @Test
    @DisplayName("LV-COORD-016: assistant response complete persists turn and returns to Listening")
    fun lvCoord016_assistantResponseCompleteReturnsToListening() =
        runTest {
            stubOpenSession()
            coEvery {
                persistVoiceTurnUseCase(null, "", "Hi there")
            } returns ChatId("c1")
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
            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(null, "", "Hi there")
            }
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

            fakeCapture.emit(loudPcm16Frame())
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

            coVerify(exactly = 0) { persistVoiceTurnUseCase(any(), any(), any()) }

            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("Persona engaged."))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(
                    ChatId("c1"),
                    "I want you to take on the persona of a talking, yelling at their deadbeat boyfriend.",
                    "Persona engaged.",
                )
            }
        }

    @Test
    @DisplayName("LV-COORD-021: local barge-in persists user turn and partial assistant transcript")
    fun lvCoord021_localBargeInPersistsPartialAssistantTurn() =
        runTest {
            stubOpenSession()
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "clean the fish", "How to clean a fish")
            } returns ChatId("c1")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("clean the fish"))
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("How to clean a fish"))
            advanceUntilIdle()

            fakeCapture.emit(loudPcm16Frame())
            advanceUntilIdle()

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "clean the fish", "How to clean a fish")
            }
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

            coVerify(exactly = 0) { persistVoiceTurnUseCase(any(), any(), any()) }
            assertTrue(fakePlayer.stopped)
        }

    @Test
    @DisplayName("LV-COORD-024: duplicate AssistantResponseComplete persists exactly one turn")
    fun lvCoord024_duplicateAssistantResponseCompletePersistsOnce() =
        runTest {
            stubOpenSession()
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "hello", "full assistant reply")
            } returns ChatId("c1")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hello"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("full assistant reply"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "hello", "full assistant reply")
            }
        }

    @Test
    @DisplayName("LV-COORD-026: late assistant events after barge-in persist do not create a second message")
    fun lvCoord026_lateEventsAfterBargeInPersistDoNotSplitTurn() =
        runTest {
            stubOpenSession()
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "clean the fish", "How to clean a fish")
            } returns ChatId("c1")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = false)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("clean the fish"))
            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(1)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("How to clean a fish"))
            advanceUntilIdle()

            fakeCapture.emit(loudPcm16Frame())
            advanceUntilIdle()

            fakeSession.emit(LiveVoiceEvent.AssistantAudioChunk(byteArrayOf(2)))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("How to clean a fish completely"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "clean the fish", "How to clean a fish")
            }
        }

    @Test
    @DisplayName("LV-COORD-025: response complete waits for final assistant transcript before persisting")
    fun lvCoord025_responseCompleteWaitsForFinalTranscript() =
        runTest {
            stubOpenSession()
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "hello", "final transcript")
            } returns ChatId("c1")
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            coordinator.start(scope, chatId = ChatId("c1"))
            fakeSession.emit(LiveVoiceEvent.Connected)
            fakeCapture.emitSession(echoCancellationActive = true)
            fakeSession.emit(LiveVoiceEvent.UserTranscriptDone("hello"))
            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDelta("final"))
            fakeSession.emit(LiveVoiceEvent.AssistantResponseComplete)
            advanceUntilIdle()

            coVerify(exactly = 0) { persistVoiceTurnUseCase(any(), any(), any()) }

            fakeSession.emit(LiveVoiceEvent.AssistantTranscriptDone("final transcript"))
            advanceUntilIdle()

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "hello", "final transcript")
            }
        }

    @Test
    @DisplayName("LV-COORD-020: each completed assistant response persists its turn immediately")
    fun lvCoord020_persistsEachCompletedTurn() =
        runTest {
            stubOpenSession()
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "first question", "first answer")
            } returns ChatId("c1")
            coEvery {
                persistVoiceTurnUseCase(ChatId("c1"), "second question", "second answer")
            } returns ChatId("c1")
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

            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "first question", "first answer")
            }
            coVerify(exactly = 1) {
                persistVoiceTurnUseCase(ChatId("c1"), "second question", "second answer")
            }
        }

    private class FakeLiveVoiceSession : LiveVoiceSession {
        private val eventsFlow = MutableSharedFlow<LiveVoiceEvent>(extraBufferCapacity = 16)
        val sentAudio = mutableListOf<ByteArray>()
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

        fun loudPcm16Frame(): ByteArray {
            val low = (LOUD_SAMPLE and 0xFF).toByte()
            val high = ((LOUD_SAMPLE shr 8) and 0xFF).toByte()
            return byteArrayOf(low, high, low, high)
        }

        fun quietPcm16Frame(): ByteArray {
            val low = (QUIET_SAMPLE and 0xFF).toByte()
            val high = ((QUIET_SAMPLE shr 8) and 0xFF).toByte()
            return byteArrayOf(low, high, low, high)
        }
    }
}