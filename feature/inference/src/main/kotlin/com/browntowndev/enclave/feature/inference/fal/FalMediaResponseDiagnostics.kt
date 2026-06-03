package com.browntowndev.enclave.feature.inference.fal

import android.util.Log
import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object FalMediaResponseDiagnostics {
    private val json = Json { ignoreUnknownKeys = true }

    private const val TAG = "FalMediaGen"
    private const val LOG_BODY_MAX_CHARS = 4_096
    private const val HTTP_ERROR_MAX_CHARS = 512
    private const val URL_LOG_MAX_CHARS = 256

    fun logResultFetchFailure(
        modelId: String,
        fetchUrl: String,
        error: Throwable,
    ) {
        Log.w(TAG, "fal result fetch failed modelId=$modelId url=$fetchUrl error=${error.message}")
    }

    fun logVideoUrlExtractionFailure(
        modelId: String,
        request: FalRequest,
        payload: JsonObject,
        outputSchema: ModelGenerationOutputSchema?,
        resultFetchFailed: Boolean,
        resultFetchUrl: String?,
    ) {
        val errorHint = extractPayloadErrorMessage(payload)
        Log.w(
            TAG,
            buildString {
                append("No fal video URL extracted")
                append(" modelId=").append(modelId)
                append(" requestId=").append(request.requestId)
                append(" statusUrl=").append(request.statusUrl)
                append(" responseUrl=").append(request.responseUrl)
                if (resultFetchUrl != null) {
                    append(" resultFetchUrl=").append(resultFetchUrl)
                    append(" resultFetchFailed=").append(resultFetchFailed)
                }
                append(" payloadKeys=").append(payload.keys.sorted())
                if (errorHint != null) append(" apiError=").append(errorHint)
                append(" schemaPaths=").append(outputSchema?.mediaUrlPaths?.size ?: 0)
                append(" extraction=").append(describeExtraction(payload, outputSchema))
                append(" body=").append(sanitizeJsonForLog(payload.toString()))
            },
        )
    }

    fun buildVideoUrlMissingMessage(
        modelId: String,
        payload: JsonObject,
        resultFetchFailed: Boolean,
        resultFetchUrl: String?,
    ): String {
        val errorHint = extractPayloadErrorMessage(payload)
        return buildString {
            append("No fal video URL returned for model ")
            append(modelId)
            when {
                errorHint != null -> append(" (API response: ").append(errorHint).append(')')
                resultFetchFailed && resultFetchUrl != null ->
                    append(" (result fetch failed for ").append(resultFetchUrl).append(')')
            }
            append(". See Logcat tag ")
            append(TAG)
            append(" for response body.")
        }
    }

    fun describeExtraction(
        payload: JsonObject,
        outputSchema: ModelGenerationOutputSchema?,
    ): String {
        val primaryVideo = FalResponseMediaUrls.primaryVideoUrls(payload, outputSchema)
        val primaryMedia = FalResponseMediaUrls.primaryMediaUrls(payload, outputSchema)
        val allMedia = FalResponseMediaUrls.mediaUrls(payload, outputSchema)
        val samples =
            (primaryVideo + primaryMedia + allMedia)
                .distinct()
                .take(3)
                .joinToString(prefix = "[", postfix = "]") { sanitizeUrlForLog(it) }
        return "primaryVideo=${primaryVideo.size} primaryMedia=${primaryMedia.size} all=${allMedia.size} samples=$samples"
    }

    fun extractPayloadErrorMessage(payload: JsonObject): String? {
        payload.directErrorMessage()?.let { return it }
        (payload["error"] as? JsonObject)?.directErrorMessage()?.let { return it }
        (payload["error"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    fun sanitizeJsonForLog(rawJson: String): String = sanitizeForLog(rawJson, LOG_BODY_MAX_CHARS)

    /** Compact, safe summary for exception messages and upstream error reporting. */
    fun formatHttpErrorBody(body: String): String {
        val parsed =
            runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrNull()
                ?.let { extractPayloadErrorMessage(it) }
        return parsed?.takeIf { it.isNotBlank() } ?: sanitizeForLog(body, HTTP_ERROR_MAX_CHARS)
    }

    fun sanitizeUrlForLog(url: String): String =
        when {
            url.startsWith("data:", ignoreCase = true) -> "data:<redacted>"
            url.length <= URL_LOG_MAX_CHARS -> url
            else -> url.take(URL_LOG_MAX_CHARS) + "…(truncated)"
        }

    private fun sanitizeForLog(raw: String, maxChars: Int): String {
        val redacted = raw.replace(Regex("""base64,[A-Za-z0-9+/=]+"""), "base64,<redacted>")
        return if (redacted.length <= maxChars) {
            redacted
        } else {
            redacted.take(maxChars) + "…(truncated)"
        }
    }

    private fun JsonObject.directErrorMessage(): String? {
        listOf("error", "detail", "message", "error_message", "reason", "title")
            .forEach { key ->
                when (val value = this[key]) {
                    is JsonPrimitive ->
                        value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                    is JsonArray -> value.firstErrorFromArray()?.let { return it }
                    is JsonObject -> value.directErrorMessage()?.let { return it }
                    else -> Unit
                }
            }
        return null
    }

    private fun JsonArray.firstErrorFromArray(): String? =
        firstNotNullOfOrNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonObject ->
                    element.optionalString("msg")
                        ?: element.optionalString("message")
                        ?: element.directErrorMessage()
                else -> null
            }
        }
}
