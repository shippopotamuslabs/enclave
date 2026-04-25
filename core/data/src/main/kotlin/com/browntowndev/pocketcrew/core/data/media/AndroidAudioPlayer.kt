package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.AudioPlayerPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: LoggingPort,
) : AudioPlayerPort {

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    init {
        // Initial cleanup of any stale TTS files from previous sessions
        cleanStaleTtsFiles()
    }

    override suspend fun playAudio(audioBytes: ByteArray) {
        stop()

        return suspendCancellableCoroutine { continuation ->
            try {
                // Create a temporary file to store the audio bytes
                val ext = detectAudioExtension(audioBytes)
                val file = File(context.cacheDir, "tts_playback_${UUID.randomUUID()}$ext")
                FileOutputStream(file).use { it.write(audioBytes) }
                tempFile = file

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(file))
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                    }
                    setOnCompletionListener {
                        continuation.resume(Unit)
                        stop()
                    }
                    setOnErrorListener { _, what, extra ->
                        continuation.resume(Unit)
                        stop()
                        true
                    }
                }

                continuation.invokeOnCancellation {
                    stop()
                }
            } catch (e: Exception) {
                continuation.resume(Unit)
                stop()
            }
        }
    }

    override fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            tempFile?.delete()
            tempFile = null
        } catch (e: Exception) {
            logger.error("AndroidAudioPlayer", "Error stopping playback or cleaning temp file", e)
        }
    }

    private fun cleanStaleTtsFiles() {
        try {
            context.cacheDir.listFiles { _, name ->
                name.startsWith("tts_playback_")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            logger.error("AndroidAudioPlayer", "Error during stale TTS file cleanup", e)
        }
    }

    /**
     * Detects the audio container format from the magic bytes at the start of [audioBytes]
     * and returns an appropriate file extension (e.g. ".wav" or ".mp3").
     */
    private fun detectAudioExtension(audioBytes: ByteArray): String {
        return if (audioBytes.size >= 4 &&
            audioBytes[0] == 'R'.code.toByte() &&
            audioBytes[1] == 'I'.code.toByte() &&
            audioBytes[2] == 'F'.code.toByte() &&
            audioBytes[3] == 'F'.code.toByte()
        ) {
            ".wav"
        } else {
            ".mp3"
        }
    }
}
