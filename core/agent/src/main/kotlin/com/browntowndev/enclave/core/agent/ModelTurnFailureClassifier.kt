package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ModelApiTransportHeuristics

/**
 * Maps host model-transport failures into Rust [`submit_model_turn_failure`] reports.
 */
internal object ModelTurnFailureClassifier {
    private const val HTTP_STATUS_MIN = 100
    private const val HTTP_STATUS_MAX = 599

    fun classify(
        error: Throwable,
        partialOutputChars: Int,
    ): FfiModelTurnFailureReport {
        val message = buildMessage(error)
        val httpStatus = extractHttpStatus(message)
        val errorClass =
            if (isPermanent(error, message, httpStatus)) {
                FfiModelApiErrorClass.PERMANENT
            } else {
                FfiModelApiErrorClass.TRANSIENT
            }
        return FfiModelTurnFailureReport(
            errorClass = errorClass,
            httpStatus = httpStatus,
            providerCode = extractProviderCode(message),
            message = message,
            hadPartialOutput = partialOutputChars > 0,
            partialOutputChars = partialOutputChars.coerceAtLeast(0).toUInt(),
            retryAfterMs = extractRetryAfterMs(message),
        )
    }

    private fun buildMessage(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return buildString {
            append(root::class.simpleName ?: "Error")
            root.message?.let { append(": ").append(it) }
            if (error !== root) {
                error.message?.let { append(" [").append(it).append(']') }
            }
        }
    }

    private fun extractHttpStatus(message: String): UShort? {
        val patterns =
            listOf(
                Regex("""code[=:]\s*(\d{3})""", RegexOption.IGNORE_CASE),
                Regex("""\bHTTP\s+(\d{3})\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(\d{3}):\s"""),
            )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern
                .find(message)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it in HTTP_STATUS_MIN..HTTP_STATUS_MAX }
                ?.toUShort()
        }
    }

    private fun extractProviderCode(message: String): String? {
        val match =
            Regex("""provider_code[=:]\s*"?([^",}\s]+)"?""", RegexOption.IGNORE_CASE)
                .find(message)
        return match?.groupValues?.getOrNull(1)
    }

    private fun extractRetryAfterMs(message: String): ULong? {
        val match =
            Regex("""retry[-_ ]after[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(message)
        return match?.groupValues?.getOrNull(1)?.toULongOrNull()
    }

    private fun isPermanent(
        error: Throwable,
        message: String,
        httpStatus: UShort?,
    ): Boolean =
        !ModelApiTransportHeuristics.shouldRetryModelApiFailure(
            error = error,
            normalizedMessage = message.lowercase(),
            httpStatus = httpStatus?.toInt(),
        )
}
