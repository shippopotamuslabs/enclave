package com.browntowndev.pocketcrew.core.data.media

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class AndroidAudioCaptureTest {

    @Test
    fun audioChunks_invalidMinBufferSize_throws() = runTest {
        val capture = AndroidAudioCapture(
            audioRecordFactory = FakeAudioRecordFactory(
                minBufferSize = 0,
                recorder = FakeAudioRecorder(emptyList()),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            capture.audioChunks().first()
        }
    }

    @Test
    fun audioChunks_normalizesPcmShortsToFloats() = runTest {
        val recorder = FakeAudioRecorder(
            reads = listOf(
                shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE),
            ),
        )
        val capture = AndroidAudioCapture(
            audioRecordFactory = FakeAudioRecordFactory(
                minBufferSize = 6,
                recorder = recorder,
            ),
        )

        capture.audioChunks().test {
            val chunk = awaitItem()
            assertEquals(-1.0f, chunk[0])
            assertEquals(0.0f, chunk[1])
            assertEquals(Short.MAX_VALUE / 32_768.0f, chunk[2])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun audioChunks_cancellationStopsAndReleasesRecorder() = runTest {
        val recorder = FakeAudioRecorder(
            reads = generateSequence { shortArrayOf(1000, 1000) }.take(10).toList(),
        )
        val capture = AndroidAudioCapture(
            audioRecordFactory = FakeAudioRecordFactory(
                minBufferSize = 4,
                recorder = recorder,
            ),
        )

        capture.audioChunks().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(recorder.started)
        assertTrue(recorder.stopped)
        assertTrue(recorder.released)
    }

    private class FakeAudioRecordFactory(
        private val minBufferSize: Int,
        private val recorder: FakeAudioRecorder,
    ) : AudioRecordFactory {
        override fun getMinBufferSize(): Int = minBufferSize
        override fun create(bufferSizeInBytes: Int): AudioRecorder = recorder
    }

    private class FakeAudioRecorder(
        private val reads: List<ShortArray>,
    ) : AudioRecorder {
        var started = false
        var stopped = false
        var released = false
        private var index = 0

        override fun startRecording() {
            started = true
        }

        override fun read(buffer: ShortArray, size: Int): Int {
            val next = reads.getOrNull(index) ?: return 0
            index += 1
            next.copyInto(buffer, endIndex = next.size.coerceAtMost(size))
            return next.size.coerceAtMost(size)
        }

        override fun stop() {
            stopped = true
        }

        override fun release() {
            released = true
        }
    }
}
