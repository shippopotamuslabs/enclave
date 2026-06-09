package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ModelApiTransportHeuristics

/**
 * Maps host model-transport failures into Rust [`submit_model_turn_failure`] reports.
 */
internal object ModelTurnFailureClassifier {
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
        for (pattern in patterns) {
            val match = pattern.find(message) ?: continue
            val code = match.groupValues[1].toIntOrNull() ?: continue
            if (code in 100..599) {
                return code.toUShort()
            }
        }
        return null
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
    ): Boolean {
        val status = httpStatus?.toInt()
        if (ModelApiTransportHeuristics.isTransientHttpStatus(status)) {
            return false
        }
        if (ModelApiTransportHeuristics.isPermanentHttpStatus(status)) {
            return true
        }
        val normalized = message.lowercase()
        if (
            ModelApiTransportHeuristics.isTransientTransportMessage(normalized) ||
            ModelApiTransportHeuristics.isTransientTransportErrorType(error)
        ) {
            return false
        }
        if (ModelApiTransportHeuristics.isPermanentTransportMessage(normalized)) {
            return true
        }
        if (status != null && status in 400..499) {
            return true
        }
        return true
    }
}