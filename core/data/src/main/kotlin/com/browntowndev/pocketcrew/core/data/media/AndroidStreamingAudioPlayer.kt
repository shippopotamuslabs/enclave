package com.browntowndev.pocketcrew.core.data.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.StreamingAudioConfig
import com.browntowndev.pocketcrew.domain.port.media.StreamingAudioPlayerPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [StreamingAudioPlayerPort] using [AudioTrack] for low-latency
 * streaming PCM audio playback.
 *
 * This implementation writes audio chunks directly to the audio hardware as they arrive,
 * enabling playback to begin as soon as the first chunk is received rather than waiting
 * for a complete file.
 *
 * Thread safety: This class is thread-safe. All public methods use a [Mutex] to coordinate
 * access to the [AudioTrack] instance.
 */
@Singleton
class AndroidStreamingAudioPlayer @Inject constructor(
    private val logger: LoggingPort,
) : StreamingAudioPlayerPort {

    private var audioTrack: AudioTrack? = null
    private var currentConfig: StreamingAudioConfig? = null
    private val mutex = Mutex()

    /**
     * Minimum buffer size in bytes before we start playback.
     * This provides a small jitter buffer while keeping latency low.
     * At 24kHz 16-bit mono, 4800 bytes = 100ms of audio.
     */
    private companion object {
        const val MIN_BUFFER_BEFORE_PLAYBACK_BYTES = 4800
        const val TAG = "AndroidStreamingAudioPlayer"
    }

    override suspend fun initialize(config: StreamingAudioConfig): Unit = mutex.withLock {
        // Clean up any existing instance
        stopInternal()

        val sampleRate = config.sampleRate
        val channelConfig = if (config.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val audioFormat = when (config.bitsPerSample) {
            8 -> AudioFormat.ENCODING_PCM_8BIT
            16 -> AudioFormat.ENCODING_PCM_16BIT
            else -> throw IllegalArgumentException("Unsupported bitsPerSample: ${config.bitsPerSample}")
        }

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(MIN_BUFFER_BEFORE_PLAYBACK_BYTES * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                if (it.state == AudioTrack.STATE_UNINITIALIZED) {
                    throw IllegalStateException("Failed to initialize AudioTrack")
                }
            }

        currentConfig = config
        logger.debug(TAG, "Initialized with config: ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitsPerSample}bit")
    }

    override suspend fun enqueueChunk(audioChunk: ByteArray): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val track = audioTrack
                ?: throw IllegalStateException("AudioTrack not initialized. Call initialize() first.")

            // Write the chunk to the AudioTrack's streaming buffer
            var offset = 0
            val bytesToWrite = audioChunk.size

            while (offset < bytesToWrite) {
                val written = track.write(audioChunk, offset, bytesToWrite - offset)
                if (written < 0) {
                    logger.error(TAG, "AudioTrack write error: $written")
                    throw IllegalStateException("AudioTrack write failed with error code: $written")
                }
                offset += written
            }

            logger.debug(TAG, "Enqueued ${audioChunk.size} bytes (total written: $offset)")
        }
    }

    override fun startPlayback() {
        audioTrack?.play()
            ?: throw IllegalStateException("AudioTrack not initialized. Call initialize() first.")
        logger.debug(TAG, "Playback started")
    }

    override fun stop() {
        runCatching {
            stopInternal()
        }.onFailure { e ->
            logger.error(TAG, "Error during stop", e)
        }
    }

    private fun stopInternal() {
        audioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
            logger.debug(TAG, "AudioTrack stopped and released")
        }
        audioTrack = null
        currentConfig = null
    }

    override fun isInitialized(): Boolean = audioTrack?.let {
        it.state != AudioTrack.STATE_UNINITIALIZED
    } ?: false

}
