package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.concurrent.TimeUnit

class GeminiLiveSession(
    private val asset: LiveVoiceProviderAsset,
    private val apiKey: String,
) : LiveVoiceSession {
    private val httpClient =
        OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    @Volatile
    private var webSocket: WebSocket? = null

    override fun events(): Flow<LiveVoiceEvent> =
        callbackFlow {
            val request =
                Request
                    .Builder()
                    .url(buildGeminiLiveWebSocketUrl())
                    .header("x-goog-api-key", apiKey)
                    .build()

            val listener =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        sendSetupMessage(webSocket)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        parseGeminiLiveEvent(text)?.let { trySend(it) }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        trySend(LiveVoiceEvent.Error(t.message ?: "WebSocket connection failed"))
                        close()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        trySend(LiveVoiceEvent.Closed)
                        close()
                    }
                }

            webSocket = httpClient.newWebSocket(request, listener)

            awaitClose {
                webSocket?.close(1000, "session closed")
                webSocket = null
                shutdownHttpClient()
            }
        }

    override suspend fun sendAudio(pcm16: ByteArray) {
        val payload =
            buildJsonObject {
                put(
                    "realtimeInput",
                    buildJsonObject {
                        put(
                            "mediaChunks",
                            kotlinx.serialization.json.JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", Base64.getEncoder().encodeToString(pcm16))
                                    },
                                ),
                            ),
                        )
                    },
                )
            }
        webSocket?.send(payload.toString())
    }

    override suspend fun commitTurn() {
        // Gemini Live uses continuous streaming; no explicit commit required.
    }

    override suspend fun cancelAssistantResponse() {
        // Gemini Live relies on new user audio to take the floor; no explicit cancel event.
    }

    override suspend fun close() {
        webSocket?.close(1000, "client closed")
        webSocket = null
        shutdownHttpClient()
    }

    private fun shutdownHttpClient() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun buildGeminiLiveWebSocketUrl(): String {
        val baseUrl = asset.baseUrl ?: ApiProvider.GOOGLE.defaultBaseUrl() ?: DEFAULT_GOOGLE_BASE_URL
        val wsBase =
            baseUrl
                .removeSuffix("/")
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
        val model = asset.modelName ?: DEFAULT_GEMINI_MODEL
        return "$wsBase/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?model=$model"
    }

    private fun sendSetupMessage(webSocket: WebSocket) {
        val model = asset.modelName ?: DEFAULT_GEMINI_MODEL
        val payload =
            buildJsonObject {
                put(
                    "setup",
                    buildJsonObject {
                        put("model", "models/$model")
                        put(
                            "generationConfig",
                            buildJsonObject {
                                put(
                                    "speechConfig",
                                    buildJsonObject {
                                        put(
                                            "voiceConfig",
                                            buildJsonObject {
                                                put(
                                                    "prebuiltVoiceConfig",
                                                    buildJsonObject {
                                                        put("voiceName", asset.voiceName)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        webSocket.send(payload.toString())
    }

    private companion object {
        const val DEFAULT_GOOGLE_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash-live-001"
    }
}
