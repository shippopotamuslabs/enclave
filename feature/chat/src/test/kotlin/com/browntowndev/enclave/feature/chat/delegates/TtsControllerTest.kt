package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsControllerTest {
    @Test
    fun `play error routes failure through errorHandler with user message`() =
        runTest {
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }

            val errorMessage = "API key not found for TTS provider: Grok TTS"
            every { playbackController.play(any()) } returns
                flowOf(TtsPlaybackStatus.Error(message = errorMessage, cause = null))
            every { playbackController.stop() } just Runs

            val controller = TtsController(playbackController, loggingPort, errorHandler)

            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            verify {
                errorHandler.handleError(
                    tag = any(),
                    message = any(),
                    throwable = any(),
                    userMessage =
                        match {
                            it.contains("Audio playback failed") &&
                                it.contains("API key not found for TTS provider: Grok TTS")
                        },
                )
            }
            assertFalse(controller.isPlayingTts.value)
        }

    @Test
    fun `isLoadingTts toggles true on Initializing and false on Playing`() =
        runTest {
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }

            val loadingSnapshots = mutableListOf<Boolean>()
            every { playbackController.play(any()) } returns
                flow {
                    emit(TtsPlaybackStatus.Initializing)
                    loadingSnapshots += true
                    emit(TtsPlaybackStatus.Playing)
                }
            every { playbackController.stop() } just Runs

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
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            val controller = TtsController(playbackController, loggingPort, errorHandler)

            controller.onPauseTts()

            verify { playbackController.pause() }
        }

    @Test
    fun `TtsPlaybackStatus Paused sets isPausedTts true and isPlayingTts false`() =
        runTest {
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }

            every { playbackController.play(any()) } returns
                flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            assertTrue(controller.isPausedTts.value)
            assertFalse(controller.isPlayingTts.value)
        }

    @Test
    fun `onPlayTts resumes when paused with same text`() =
        runTest {
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }

            every { playbackController.play(any()) } returns
                flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            // Initial play -> transitions to paused
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)
            assertTrue(controller.isPausedTts.value)

            // Play same text -> triggers resume instead of play
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)

            verify(exactly = 1) { playbackController.play("hello") }
            verify(exactly = 1) { playbackController.resume() }
        }

    @Test
    fun `onPlayTts stops prior and plays new when paused with different text`() =
        runTest {
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }

            every { playbackController.play(any()) } returns
                flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            // Initial play -> transitions to paused
            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)
            assertTrue(controller.isPausedTts.value)

            // Play different text
            controller.onPlayTts(text = "world", scope = unconfinedScope, hasTtsProviderAssigned = true)

            verify(exactly = 1) { playbackController.play("hello") }
            verify(exactly = 1) { playbackController.play("world") }
            verify(atLeast = 1) { playbackController.stop() }
            verify(exactly = 0) { playbackController.resume() }
        }

    @Test
    fun `onStopTts stops and resets all states including isPausedTts`() =
        runTest {
            val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }

            every { playbackController.play(any()) } returns
                flowOf(TtsPlaybackStatus.Paused)

            val controller = TtsController(playbackController, loggingPort, errorHandler)
            val unconfinedScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

            controller.onPlayTts(text = "hello", scope = unconfinedScope, hasTtsProviderAssigned = true)
            assertTrue(controller.isPausedTts.value)

            controller.onStopTts()

            assertFalse(controller.isPausedTts.value)
            assertFalse(controller.isPlayingTts.value)
            verify(atLeast = 1) { playbackController.stop() }
        }
}
