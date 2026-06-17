package com.browntowndev.enclave.feature.inference.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import androidx.core.content.ContextCompat
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureEvent
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureSessionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeAudioCaptureImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RealtimeAudioCapturePort {
        override fun captureEvents(sampleRate: Int): Flow<RealtimeCaptureEvent> =
            flow {
                require(sampleRate > 0) { "Sample rate must be positive: $sampleRate" }
                require(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                ) {
                    "RECORD_AUDIO permission is required to capture microphone input"
                }

                val minBufferSize =
                    AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                require(minBufferSize > 0) { "Invalid AudioRecord buffer size: $minBufferSize" }

                val bufferSize = minBufferSize * BUFFER_MULTIPLIER
                val audioFormat =
                    AudioFormat
                        .Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()

                val audioRecord =
                    try {
                        AudioRecord
                            .Builder()
                            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(bufferSize)
                            .build()
                    } catch (exception: SecurityException) {
                        throw IllegalStateException(
                            "RECORD_AUDIO permission is required to capture microphone input",
                            exception,
                        )
                    }

                require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    "AudioRecord failed to initialize"
                }

                val echoCanceler =
                    if (AcousticEchoCanceler.isAvailable()) {
                        AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true }
                    } else {
                        null
                    }
                val automaticGainControl =
                    if (AutomaticGainControl.isAvailable()) {
                        AutomaticGainControl.create(audioRecord.audioSessionId)?.apply { enabled = true }
                    } else {
                        null
                    }

                emit(
                    RealtimeCaptureEvent.SessionStarted(
                        RealtimeCaptureSessionInfo(
                            audioSessionId = audioRecord.audioSessionId,
                            isEchoCancellationActive = echoCanceler?.enabled == true,
                        ),
                    ),
                )

                val frameSamples =
                    (sampleRate * CAPTURE_FRAME_DURATION_MS / MILLIS_PER_SECOND)
                        .coerceAtLeast(MIN_CAPTURE_FRAME_SAMPLES)
                val shortBuffer = ShortArray(frameSamples)
                try {
                    audioRecord.startRecording()
                    require(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        "AudioRecord failed to start recording"
                    }
                    while (currentCoroutineContext().isActive) {
                        when (val readCount = audioRecord.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)) {
                            0 -> Unit
                            in 1..shortBuffer.size ->
                                emit(
                                    RealtimeCaptureEvent.Pcm16Frame(
                                        shortBuffer.toPcm16Le(readCount),
                                    ),
                                )
                            else -> throw IllegalStateException("AudioRecord read failed: $readCount")
                        }
                    }
                } finally {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                    audioRecord.release()
                    echoCanceler?.release()
                    automaticGainControl?.release()
                }
            }.flowOn(Dispatchers.IO)

        private fun ShortArray.toPcm16Le(readCount: Int): ByteArray {
            val bytes = ByteArray(readCount * BYTES_PER_SAMPLE)
            for (index in 0 until readCount) {
                val sample = this[index]
                bytes[index * BYTES_PER_SAMPLE] = (sample.toInt() and 0xFF).toByte()
                bytes[index * BYTES_PER_SAMPLE + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        private companion object {
            const val BUFFER_MULTIPLIER = 2
            const val BYTES_PER_SAMPLE = 2
            const val CAPTURE_FRAME_DURATION_MS = 20
            const val MILLIS_PER_SECOND = 1000
            const val MIN_CAPTURE_FRAME_SAMPLES = 160
        }
    }
