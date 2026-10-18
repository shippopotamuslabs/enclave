package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsControllerTest {
    @Test
    fun `play error routes failure through errorHandler with user message`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            val errorMessage = "API key not found for TTS provider: Grok TTS"
            playbackController.playbackFlow =
                flowOf(TtsPlaybackStatus.Error(message = errorMessage, cause = null))

            val controller = TtsController(playbackController, loggingPort, errorHandler)

            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            assertTrue(
                errorHandler.handledUserMessages.any {
                    it.contains("Audio playback failed") &&
                        it.contains("API key not found for TTS provider: Grok TTS")
                },
            )
            assertFalse(controller.isPlayingTts.value)
        }

    @Test
    fun `isLoadingTts toggles true on Initializing and false on Playing`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            val loadingSnapshots = mutableListOf<Boolean>()
            playbackController.playbackFlow =
                flow {
                    emit(TtsPlaybackStatus.Initializing)
                    loadingSnapshots += true
                    emit(TtsPlaybackStatus.Playing)
                }

            val controller = TtsController(playbackController, loggingPort, errorHandler)

            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            assertTrue(loadingSnapshots.first())
            assertFalse(controller.isLoadingTts.value)
            assertTrue(controller.isPlayingTts.value)
        }

    @Test
    fun `onPauseTts calls pause on playbackController`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()
            val controller = TtsController(playbackController, loggingPort, errorHandler)

            controller.onPauseTts()

            assertEquals(1, playbackController.pauseCount)
        }

    @Test
    fun `TtsPlaybackStatus Paused sets isPausedTts true and isPlayingTts false`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            playbackController.playbackFlow = flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            assertTrue(controller.isPausedTts.value)
            assertFalse(controller.isPlayingTts.value)
        }

    @Test
    fun `onPlayTts resumes when paused with same text`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            playbackController.playbackFlow = flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            // Initial play -> transitions to paused
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)
            assertTrue(controller.isPausedTts.value)

            // Play same text -> triggers resume instead of play
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            assertEquals(listOf("hello"), playbackController.playCalls)
            assertEquals(1, playbackController.resumeCount)
        }

    @Test
    fun `onPlayTts stops prior and plays new when paused with different text`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            playbackController.playbackFlow = flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            // Initial play -> transitions to paused
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)
            assertTrue(controller.isPausedTts.value)

            // Play different text
            controller.onPlayTts(text = "world", scope = unconfinedScope, hasTtsProviderAssigned = true)

            assertEquals(listOf("hello", "world"), playbackController.playCalls)
            assertTrue(playbackController.stopCount >= 1)
            assertEquals(0, playbackController.resumeCount)
        }

    @Test
    fun `onStopTts stops and resets all states including isPausedTts`() =
        runTest {
            val playbackController = FakeTtsPlaybackController()
            val loggingPort = FakeLoggingPort()
            val errorHandler = FakeViewModelErrorHandler()

            playbackController.playbackFlow = flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)
            assertTrue(controller.isPausedTts.value)

            controller.onStopTts()

            assertFalse(controller.isPausedTts.value)
            assertFalse(controller.isPlayingTts.value)
            assertTrue(playbackController.stopCount >= 1)
        }
}
