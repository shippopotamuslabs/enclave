package com.browntowndev.pocketcrew.core.data.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.browntowndev.pocketcrew.domain.port.media.AudioCapturePort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

@Singleton
class AndroidAudioCapture @Inject constructor(
    private val audioRecordFactory: AudioRecordFactory,
) : AudioCapturePort {

    override fun audioChunks(): Flow<FloatArray> = flow {
        val minBufferSize = audioRecordFactory.getMinBufferSize()
        require(minBufferSize > 0) { "Invalid AudioRecord buffer size: $minBufferSize" }

        val bufferSize = minBufferSize * BUFFER_MULTIPLIER
        val recorder = audioRecordFactory.create(bufferSize)
        val shortBuffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)

        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                when (val readCount = recorder.read(shortBuffer, shortBuffer.size)) {
                    0 -> Unit
                    in 1..shortBuffer.size -> emit(shortBuffer.toFloatArray(readCount))
                    else -> throw IllegalStateException("AudioRecord read failed: $readCount")
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun ShortArray.toFloatArray(readCount: Int): FloatArray {
        return FloatArray(readCount) { index ->
            this[index] / PCM_16_NORMALIZATION_FACTOR
        }
    }

    private companion object {
        const val BUFFER_MULTIPLIER = 2
        const val BYTES_PER_SAMPLE = 2
        const val PCM_16_NORMALIZATION_FACTOR = 32_768.0f
    }
}

interface AudioRecordFactory {
    fun getMinBufferSize(): Int
    fun create(bufferSizeInBytes: Int): AudioRecorder
}

interface AudioRecorder {
    fun startRecording()
    fun read(buffer: ShortArray, size: Int): Int
    fun stop()
    fun release()
}

@Singleton
class AndroidAudioRecordFactory @Inject constructor() : AudioRecordFactory {
    override fun getMinBufferSize(): Int {
        return AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
    }

    override fun create(bufferSizeInBytes: Int): AudioRecorder {
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build()

        return AndroidAudioRecorder(audioRecord)
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}

private class AndroidAudioRecorder(
    private val audioRecord: AudioRecord,
) : AudioRecorder {
    override fun startRecording() {
        audioRecord.startRecording()
    }

    override fun read(buffer: ShortArray, size: Int): Int {
        return audioRecord.read(buffer, 0, size, AudioRecord.READ_BLOCKING)
    }

    override fun stop() {
        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
        }
    }

    override fun release() {
        audioRecord.release()
    }
}
