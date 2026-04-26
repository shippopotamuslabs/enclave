package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStream
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamServicePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * xAI TTS media stream adapter that uses WebSocket with codec=mp3 for ExoPlayer.
 *
 * Unlike [com.browntowndev.pocketcrew.feature.inference.tts.XAiStreamingTtsService]
 * which streams PCM chunks via a Flow, this adapter uses the MP3 codec so ExoPlayer
 * can decode the audio natively without PCM configuration.
 *
 * The audio deltas from the WebSocket are base64-decoded and written into a
 * [PipedOutputStream] that backs a [PipedInputStream], which ExoPlayer's DataSource
 * reads from incrementally.
 */
class XAiMediaStreamService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val logger: LoggingPort,
) : TtsMediaStreamServicePort {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun openStream(text: String, voice: String, modelId: String?): TtsMediaStream =
        withContext(Dispatchers.IO) {
            // Use MP3 codec for ExoPlayer compatibility (no PCM config needed)
            val webSocketUrl = buildString {
                append("wss://api.x.ai/v1/tts")
                append("?language=auto")
                append("&voice=")
                append(URLEncoder.encode(voice, "UTF-8"))
                append("&codec=mp3")
                append("&sample_rate=24000")
                append("&bit_rate=128000")
                append("&optimize_streaming_latency=1")
            }

            val request = Request.Builder()
                .url(webSocketUrl)
                .header("Authorization", "Bearer $apiKey")
                .build()

            // Create piped stream for ExoPlayer to read from
            val pipedOut = PipedOutputStream()
            val pipedIn = PipedInputStream(pipedOut, PIPE_BUFFER_SIZE)

            val webSocketClient = httpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

            val streamError = AtomicReference<Throwable?>(null)

            webSocketClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    logger.debug(TAG, "WebSocket opened for MP3 stream")

                    val textMessage = json.encodeToString(
                        ClientMessage.serializer(),
                        ClientMessage(type = "text.delta", delta = text)
                    )
                    webSocket.send(textMessage)

                    val doneMessage = json.encodeToString(
                        ClientMessage.serializer(),
                        ClientMessage(type = "text.done")
                    )
                    webSocket.send(doneMessage)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val message = json.decodeFromString(ServerMessage.serializer(), text)
                        when (message.type) {
                            "audio.delta" -> {
                                message.delta?.let { delta ->
                                    try {
                                        val audioBytes = java.util.Base64.getDecoder().decode(delta)
                                        pipedOut.write(audioBytes)
                                        pipedOut.flush()
                                    } catch (e: IllegalArgumentException) {
                                        logger.error(TAG, "Failed to decode base64 audio", e)
                                    }
                                }
                            }
                            "audio.done" -> {
                                logger.debug(TAG, "MP3 stream complete")
                                closeStream(pipedOut)
                            }
                            "error" -> {
                                streamError.set(IllegalStateException(message.message ?: "xAI stream error"))
                                closeStream(pipedOut)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(TAG, "Failed to parse server message", e)
                        streamError.set(e)
                        closeStream(pipedOut)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closeStream(pipedOut)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    streamError.set(t)
                    closeStream(pipedOut)
                }
            })

            TtsMediaStream(
                mimeType = MIME_TYPE_MP3,
                length = null, // Unknown length for streaming
                stream = ErrorAwareInputStream(pipedIn, streamError),
            )
        }

    private fun closeStream(outputStream: PipedOutputStream) {
        runCatching { outputStream.close() }
    }

    private class ErrorAwareInputStream(
        private val delegate: PipedInputStream,
        private val streamError: AtomicReference<Throwable?>,
    ) : java.io.InputStream() {
        override fun read(): Int {
            val result = delegate.read()
            throwIfStreamFailed(result)
            return result
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val result = delegate.read(buffer, offset, length)
            throwIfStreamFailed(result)
            return result
        }

        override fun close() {
            delegate.close()
        }

        private fun throwIfStreamFailed(result: Int) {
            val cause = streamError.get()
            if (result == -1 && cause != null) {
                throw IOException("xAI TTS stream failed: ${cause.message}", cause)
            }
        }
    }

    /** Exposed for testing to verify MIME type without making WebSocket calls. */
    fun expectedMimeType(): String = MIME_TYPE_MP3

    /** Confirms this service uses WebSocket streaming. */
    fun usesWebSocketStreaming(): Boolean = true

    @Serializable
    private data class ClientMessage(
        val type: String,
        val delta: String? = null,
    )

    @Serializable
    private data class ServerMessage(
        val type: String,
        val delta: String? = null,
        val message: String? = null,
        val trace_id: String? = null,
    )

    companion object {
        private const val TAG = "XAiMediaStream"
        private const val MIME_TYPE_MP3 = "audio/mpeg"
        private const val PIPE_BUFFER_SIZE = 64 * 1024
    }
}
