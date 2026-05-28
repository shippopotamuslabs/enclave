package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.sync.TelemetryUploadConfig
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
    ) : TelemetryOtlpTransport {
        override suspend fun send(payload: String): Result<Unit> {
            val endpointUrl = config.endpointUrl
            val apiKey = config.apiKey
            if (endpointUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Telemetry upload is not configured"))
            }
            return withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(resolveLogsEndpoint(endpointUrl))
                            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                            .header("Authorization", "Bearer $apiKey")
                            .header("Content-Type", JSON_MEDIA_TYPE.toString())
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Telemetry upload failed with HTTP ${response.code}")
                        }
                    }
                }.fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { error ->
                        if (error is IOException) {
                            Result.failure(error)
                        } else {
                            Result.failure(IOException(error.message ?: "Telemetry upload failed", error))
                        }
                    },
                )
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
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
