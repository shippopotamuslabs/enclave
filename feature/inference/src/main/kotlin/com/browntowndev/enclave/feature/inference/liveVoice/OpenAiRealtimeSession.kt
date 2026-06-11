package com.browntowndev.enclave.feature.inference.liveVoice

import androidx.annotation.VisibleForTesting
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
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

class OpenAiRealtimeSession(
    private val asset: LiveVoiceProviderAsset,
    private val apiKey: String,
    @get:VisibleForTesting
    internal val tools: List<ToolDefinition> = emptyList(),
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
            val requestBuilder =
                Request
                    .Builder()
                    .url(buildRealtimeWebSocketUrl())
                    .header("Authorization", "Bearer $apiKey")
            if (asset.provider == ApiProvider.OPENAI) {
                requestBuilder.header("OpenAI-Beta", "realtime=v1")
            }
            val request = requestBuilder.build()

            val listener =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        sendSessionUpdate(webSocket)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        parseOpenAiRealtimeEvents(text).forEach { trySend(it) }
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
                put("type", "input_audio_buffer.append")
                put("audio", Base64.getEncoder().encodeToString(pcm16))
            }
        webSocket?.send(payload.toString())
    }

    override suspend fun commitTurn() {
        webSocket?.send("""{"type":"input_audio_buffer.commit"}""")
    }

    override suspend fun cancelAssistantResponse() {
        webSocket?.send("""{"type":"response.cancel"}""")
    }

    override suspend fun close() {
        webSocket?.close(1000, "client closed")
        webSocket = null
        shutdownHttpClient()
    }

    override suspend fun sendToolResult(
        callId: String,
        name: String,
        resultJson: String,
    ) {
        val socket = webSocket ?: return
        buildOpenAiToolResultMessages(callId, name, resultJson).forEach { message ->
            socket.send(message)
        }
    }

    @VisibleForTesting
    internal fun attachWebSocketForTest(socket: WebSocket) {
        webSocket = socket
    }

    @VisibleForTesting
    internal fun sendSessionUpdateForTest(socket: WebSocket) {
        sendSessionUpdate(socket)
    }

    private fun shutdownHttpClient() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun buildRealtimeWebSocketUrl(): String {
        val baseUrl = asset.baseUrl ?: asset.provider.defaultBaseUrl() ?: DEFAULT_OPENAI_BASE_URL
        val wsBase =
            baseUrl
                .removeSuffix("/")
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
        val model =
            asset.modelName ?: when (asset.provider) {
                ApiProvider.XAI -> DEFAULT_XAI_MODEL
                else -> DEFAULT_OPENAI_MODEL
            }
        return "$wsBase/realtime?model=$model"
    }

    private fun sendSessionUpdate(webSocket: WebSocket) {
        val payload =
            buildJsonObject {
                put("type", "session.update")
                put(
                    "session",
                    buildJsonObject {
                        put(
                            "modalities",
                            kotlinx.serialization.json.JsonArray(
                                listOf(
                                    kotlinx.serialization.json.JsonPrimitive("audio"),
                                    kotlinx.serialization.json.JsonPrimitive("text"),
                                ),
                            ),
                        )
                        put("voice", asset.voiceName)
                        put("input_audio_format", "pcm16")
                        put("output_audio_format", "pcm16")
                        // Without this the server never emits
                        // conversation.item.input_audio_transcription.* events, so the user-side
                        // caption stays empty. whisper-1 is the OpenAI-compatible transcription model.
                        put(
                            "input_audio_transcription",
                            buildJsonObject {
                                put("model", "whisper-1")
                            },
                        )
                        val vadConfig = OpenAiRealtimeSessionVadConfig.forProvider(asset.provider)
                        put(
                            "turn_detection",
                            buildJsonObject {
                                put("type", "server_vad")
                                put("threshold", vadConfig.threshold)
                                put("prefix_padding_ms", vadConfig.prefixPaddingMs)
                                put("silence_duration_ms", vadConfig.silenceDurationMs)
                                put("interrupt_response", true)
                            },
                        )
                        applyOpenAiRealtimeToolsToSession(this, tools)
                    },
                )
            }
        webSocket.send(payload.toString())
    }

    private companion object {
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-realtime-preview"
        const val DEFAULT_XAI_MODEL = "grok-voice-think-fast-1.0"
    }
}

/** Test-visible server VAD tuning for OpenAI-compatible realtime sessions. */
internal data class OpenAiRealtimeSessionVadSettings(
    val threshold: Double,
    val prefixPaddingMs: Int,
    val silenceDurationMs: Int,
)

internal object OpenAiRealtimeSessionVadConfig {
    fun forProvider(provider: ApiProvider): OpenAiRealtimeSessionVadSettings =
        when (provider) {
            ApiProvider.XAI ->
                OpenAiRealtimeSessionVadSettings(
                    threshold = 0.85,
                    prefixPaddingMs = 333,
                    silenceDurationMs = 900,
                )
            else ->
                OpenAiRealtimeSessionVadSettings(
                    // Lower threshold improves quiet-speech pickup; AEC handles echo.
                    threshold = 0.35,
                    prefixPaddingMs = 400,
                    // Longer silence window so pauses mid-utterance do not commit the turn early.
                    silenceDurationMs = 900,
                )
        }
}
