package com.browntowndev.pocketcrew.domain.usecase.chat

import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.config.UtilityType
import com.browntowndev.pocketcrew.domain.port.inference.WhisperInferencePort
import com.browntowndev.pocketcrew.domain.port.media.AudioCapturePort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.repository.UtilityModelFilePort
import com.browntowndev.pocketcrew.domain.util.Clock
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

class ListenToSpeechUseCaseTest {

    @Test
    fun invoke_modelMissing_emitsErrorAndIdle() = runTest {
        val useCase = createUseCase(
            audioCapture = FakeAudioCapture(emptyList(), FakeClock()),
            utilityModelFile = FakeUtilityModelFile(path = null),
        )

        useCase(stopSignal = MutableStateFlow(false)).test {
            assertEquals(SpeechState.ModelLoading, awaitItem())
            assertEquals(SpeechState.Error("Whisper model is not downloaded."), awaitItem())
            assertEquals(SpeechState.Idle, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun invoke_speechThenSilence_emitsFinalText() = runTest {
        val clock = FakeClock()
        val whisper = FakeWhisperInference(
            transcripts = ArrayDeque(listOf("final words")),
        )
        val useCase = createUseCase(
            audioCapture = FakeAudioCapture(
                chunks = listOf(
                    TimedChunk(timeMs = 0L, samples = speechChunk()),
                    TimedChunk(timeMs = 1_000L, samples = speechChunk()),
                    TimedChunk(timeMs = 11_001L, samples = silenceChunk()),
                ),
                clock = clock,
            ),
            whisper = whisper,
            clock = clock,
        )

        useCase(initialText = "Existing", stopSignal = MutableStateFlow(false)).test {
            assertEquals(SpeechState.ModelLoading, awaitItem())
            assertIs<SpeechState.Listening>(awaitItem()) // Initial
            assertIs<SpeechState.Listening>(awaitItem()) // Chunk 1
            assertIs<SpeechState.Listening>(awaitItem()) // Chunk 2
            assertIs<SpeechState.Listening>(awaitItem()) // Chunk 3 (silence)
            assertEquals(SpeechState.Transcribing, awaitItem())
            assertEquals(SpeechState.FinalText("Existing final words"), awaitItem())
            assertEquals(SpeechState.Idle, awaitItem())
            awaitComplete()
        }
        assertEquals(listOf("/models/ggml-base.en.bin"), whisper.initializedPaths)
        assertEquals(1, whisper.transcribeCalls)
        assertEquals(1, whisper.closeCalls)
    }

    @Test
    fun invoke_stopSignalSet_emitsFinalText() = runTest {
        val clock = FakeClock()
        val whisper = FakeWhisperInference(
            transcripts = ArrayDeque(listOf("final words")),
        )
        val stopSignal = MutableStateFlow(false)
        val useCase = createUseCase(
            audioCapture = FakeAudioCapture(
                chunks = listOf(
                    TimedChunk(timeMs = 0L, samples = speechChunk()),
                    TimedChunk(timeMs = 1_000L, samples = speechChunk()),
                ),
                clock = clock,
            ),
            whisper = whisper,
            clock = clock,
        )

        useCase(initialText = "Existing", stopSignal = stopSignal).test {
            assertEquals(SpeechState.ModelLoading, awaitItem())
            assertIs<SpeechState.Listening>(awaitItem()) // Initial
            assertIs<SpeechState.Listening>(awaitItem()) // Chunk 1
            assertIs<SpeechState.Listening>(awaitItem()) // Chunk 2
            stopSignal.value = true
            assertEquals(SpeechState.Transcribing, awaitItem())
            assertEquals(SpeechState.FinalText("Existing final words"), awaitItem())
            assertEquals(SpeechState.Idle, awaitItem())
            awaitComplete()
        }
        assertEquals(1, whisper.transcribeCalls)
    }

    private fun createUseCase(
        audioCapture: AudioCapturePort = FakeAudioCapture(emptyList(), FakeClock()),
        whisper: FakeWhisperInference = FakeWhisperInference(),
        utilityModelFile: UtilityModelFilePort = FakeUtilityModelFile(path = "/models/ggml-base.en.bin"),
        clock: Clock = FakeClock(),
    ): ListenToSpeechUseCase {
        return ListenToSpeechUseCase(
            audioCapturePort = audioCapture,
            whisperInferencePort = whisper,
            utilityModelFilePort = utilityModelFile,
            clock = clock,
        )
    }

    private data class TimedChunk(
        val timeMs: Long,
        val samples: FloatArray,
    )

    private class FakeAudioCapture(
        private val chunks: List<TimedChunk>,
        private val clock: FakeClock,
    ) : AudioCapturePort {
        override fun audioChunks(): Flow<FloatArray> = flow {
            for (chunk in chunks) {
                clock.now = chunk.timeMs
                emit(chunk.samples)
                yield()
            }
        }
    }

    private class FakeWhisperInference(
        private val transcripts: ArrayDeque<String> = ArrayDeque(),
        private val suspendFirstTranscription: CompletableDeferred<Unit>? = null,
    ) : WhisperInferencePort {
        val initializedPaths = mutableListOf<String>()
        var transcribeCalls = 0
        var closeCalls = 0

        override suspend fun initialize(modelPath: String) {
            initializedPaths += modelPath
        }

        override suspend fun transcribe(samples: FloatArray): String {
            transcribeCalls += 1
            if (transcribeCalls == 1 && suspendFirstTranscription != null) {
                suspendFirstTranscription.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
            return transcripts.removeFirstOrNull() ?: ""
        }

        override suspend fun close() {
            closeCalls += 1
        }
    }

    private class FakeUtilityModelFile(
        private val path: String?,
    ) : UtilityModelFilePort {
        override suspend fun resolveUtilityModelPath(utilityType: UtilityType): String? {
            assertEquals(UtilityType.WHISPER, utilityType)
            return path
        }
    }

    private class FakeClock : Clock {
        var now: Long = 0L
        override fun currentTimeMillis(): Long = now
    }

    private companion object {
        fun speechChunk(): FloatArray = FloatArray(320) { 0.05f }
        fun silenceChunk(): FloatArray = FloatArray(320) { 0.0f }
    }
}
