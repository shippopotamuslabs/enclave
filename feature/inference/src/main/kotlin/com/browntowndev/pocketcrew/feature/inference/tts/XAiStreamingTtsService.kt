package com.browntowndev.pocketcrew.feature.inference.tts

import com.browntowndev.pocketcrew.domain.model.inference.TtsAudioChunk
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.StreamingTtsServicePort
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * xAI streaming TTS service implementation using WebSocket for low-latency audio streaming.
 *
 * Connects to `wss://api.x.ai/v1/tts` and streams audio chunks as they are generated,
 * enabling playback to begin before the full audio is complete.
 *
 * Protocol:
 * - WebSocket URL: wss://api.x.ai/v1/tts?voice={voice}&codec=pcm&sample_rate=24000
 * - Client sends: `{"type": "text.delta", "delta": "..."}` followed by `{"type": "text.done"}`
 * - Server sends: `{"type": "audio.delta", "delta": "<base64-audio>"}` and `{"type": "audio.done"}`
 */
class XAiStreamingTtsService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val logger: LoggingPort,
) : StreamingTtsServicePort {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    override fun synthesizeSpeechStreaming(
        text: String,
        voice: String,
        modelId: String?
    ): Flow<TtsAudioChunk> = callbackFlow {
        // Build WebSocket URL with configuration parameters
        // Use PCM format for lowest latency (no decoder overhead)
        val webSocketUrl = buildString {
            append("wss://api.x.ai/v1/tts")
            append("?language=auto")
            append("&voice=")
            append(java.net.URLEncoder.encode(voice, "UTF-8"))
            append("&codec=pcm")
            append("&sample_rate=24000")
            append("&optimize_streaming_latency=1")
        }

        val request = Request.Builder()
            .url(webSocketUrl)
            .header("Authorization", "Bearer $apiKey")
            .build()

        // Create WebSocket with extended timeouts for TTS generation
        val webSocketClient = httpClient.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val webSocket = webSocketClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger.debug(TAG, "WebSocket opened for streaming TTS")

                // Send the text in chunks to the server
                // For simplicity, send the entire text as one delta
                // In the future, could chunk by sentences for even lower latency
                val textMessage = json.encodeToString(
                    ClientMessage.serializer(),
                    ClientMessage(type = "text.delta", delta = text)
                )
                webSocket.send(textMessage)

                // Signal that all text has been sent
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
                            val audioBytes = message.delta?.let { delta ->
                                try {
                                    java.util.Base64.getDecoder().decode(delta)
                                } catch (e: IllegalArgumentException) {
                                    logger.error(TAG, "Failed to decode base64 audio", e)
                                    null
                                }
                            }
                            if (audioBytes != null && !isClosedForSend) {
                                trySend(TtsAudioChunk.Data(audioBytes))
                            }
                        }

                        "audio.done" -> {
                            logger.debug(TAG, "Audio streaming complete, trace_id: ${message.trace_id}")
                            if (!isClosedForSend) {
                                trySend(TtsAudioChunk.Done)
                                close()
                            }
                        }

                        "error" -> {
                            logger.error(TAG, "Server error: ${message.message}")
                            if (!isClosedForSend) {
                                trySend(TtsAudioChunk.Error(message.message ?: "Unknown server error"))
                                close()
                            }
                        }

                        else -> {
                            logger.debug(TAG, "Unknown message type: ${message.type}")
                        }
                    }
                } catch (e: Exception) {
                    logger.error(TAG, "Failed to parse server message", e)
                    if (!isClosedForSend) {
                        trySend(TtsAudioChunk.Error("Failed to parse server message: ${e.message}"))
                        close()
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                logger.debug(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger.debug(TAG, "WebSocket closed: $code - $reason")
                if (!isClosedForSend) {
                    trySend(TtsAudioChunk.Done)
                    close()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val responseInfo = response?.let { r ->
                    val code = r.code
                    val body = r.body?.string()?.take(200)
                    " (HTTP $code${body?.let { ", body: $it" } ?: ""})"
                } ?: ""
                logger.error(TAG, "WebSocket failure$responseInfo", t)
                if (!isClosedForSend) {
                    trySend(TtsAudioChunk.Error("WebSocket connection failed$responseInfo: ${t.message}", t))
                    close(t)
                }
            }
        })

        // Handle cancellation
        awaitClose {
            logger.debug(TAG, "Flow cancelled, closing WebSocket")
            webSocket.close(1000, "Client closed connection")
        }
    }

    private companion object {
        const val TAG = "XAiStreamingTtsService"
    }
}
