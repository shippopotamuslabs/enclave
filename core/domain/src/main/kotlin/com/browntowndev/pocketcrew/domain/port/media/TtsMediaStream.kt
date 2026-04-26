package com.browntowndev.pocketcrew.domain.port.media

import java.io.InputStream

/**
 * Represents a media stream that can be fed to ExoPlayer via a custom DataSource.
 *
 * @property mimeType The MIME type of the stream (e.g., "audio/mpeg", "audio/wav", "audio/pcm").
 * @property length The total length of the stream in bytes, or null if unknown.
 * @property stream The JVM [InputStream] providing the audio data.
 */
data class TtsMediaStream(
    val mimeType: String,
    val length: Long? = null,
    val stream: InputStream,
)