package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.sync.TelemetryUploadConfig
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.qualifier.InternalTelemetryLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpTelemetryOtlpTransport
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val config: TelemetryUploadConfig,
        @param:InternalTelemetryLogging private val loggingPort: LoggingPort,
    ) : TelemetryOtlpTransport {
        override suspend fun send(payload: String): Result<Unit> {
            val endpointUrl = config.endpointUrl
            val apiKey = config.apiKey
            if (endpointUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Telemetry upload is not configured"))
            }
            return withContext(Dispatchers.IO) {
                runCatching {
                    val authHeader = getAuthHeader(apiKey)
                    val request =
                        Request
                            .Builder()
                            .url(resolveLogsEndpoint(endpointUrl))
                            .post(payload.toByteArray(Charsets.UTF_8).toRequestBody(JSON_MEDIA_TYPE))
                            .header("Authorization", authHeader)
                            .header("Content-Type", JSON_MEDIA_TYPE.toString())
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body.string()
                            loggingPort.warning(
                                TAG,
                                "Telemetry upload failed: HTTP ${response.code}. Message: ${response.message}. Response: $errorBody",
                            )
                            throw IOException("Telemetry upload failed with HTTP ${response.code}")
                        }
                    }
                }.fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { error ->
                        loggingPort.warning(
                            TAG,
                            "Telemetry upload failed with error: ${error.message}",
                        )
                        if (error is IOException) {
                            Result.failure(error)
                        } else {
                            Result.failure(IOException(error.message ?: "Telemetry upload failed", error))
                        }
                    },
                )
            }
        }

        private fun getAuthHeader(apiKey: String): String {
            val trimmed = apiKey.trim()
            return when {
                trimmed.startsWith("Basic ") || trimmed.startsWith("Bearer ") -> trimmed
                trimmed.startsWith("glc_") -> {
                    try {
                        val base64Part = trimmed.removePrefix("glc_")
                        val decodedBytes =
                            java.util.Base64
                                .getDecoder()
                                .decode(base64Part)
                        val decodedString = String(decodedBytes, Charsets.UTF_8)
                        val match = Regex("\"o\"\\s*:\\s*\"([^\"]+)\"").find(decodedString)
                        val instanceId = match?.groupValues?.get(1)
                        if (instanceId != null) {
                            val credentials = "$instanceId:$trimmed"
                            val encoded =
                                java.util.Base64.getEncoder().encodeToString(
                                    credentials.toByteArray(Charsets.UTF_8),
                                )
                            "Basic $encoded"
                        } else {
                            "Bearer $trimmed"
                        }
                    } catch (e: Exception) {
                        loggingPort.warning(TAG, "Failed to parse glc token for basic auth: ${e.message}")
                        "Bearer $trimmed"
                    }
                }
                else -> {
                    "Basic $trimmed"
                }
            }
        }

        internal fun resolveLogsEndpoint(endpointUrl: String): String {
            val trimmed = endpointUrl.trimEnd('/')
            return when {
                trimmed.endsWith("/v1/logs") -> trimmed
                trimmed.endsWith("/otlp") -> "$trimmed/v1/logs"
                else -> "$trimmed/v1/logs"
            }
        }

        companion object {
            private const val TAG = "OkHttpTelemetryOtlpTransport"
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
