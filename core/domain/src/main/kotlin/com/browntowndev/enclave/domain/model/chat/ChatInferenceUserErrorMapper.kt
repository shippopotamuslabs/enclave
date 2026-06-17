package com.browntowndev.enclave.domain.model.chat

import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelApiTransportHeuristics
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException

/**
 * Maps inference and agent-loop failures into actionable user-facing chat messages.
 *
 * BYOK users own provider credentials; surface sanitized provider copy when available.
 */
object ChatInferenceUserErrorMapper {
    const val PROMPT_TOO_LARGE_TITLE = "Your prompt is too large for this model."
    const val GENERIC_TITLE = "Something went wrong."
    const val GENERIC_HINT = "Please try again. If you use a cloud provider, check your API key and settings."
    private const val BUSY_TITLE = "Another message is in progress. Please wait until it completes."
    private const val RATE_LIMIT_HINT =
        "If you are on a subscription plan, your usage limits may have been exceeded, " +
            "or the provider is rejecting too many requests. Wait for your limit to reset or check your account."
    private const val RETRY_EXHAUSTED_FALLBACK_TITLE = "The model provider failed after several retry attempts."
    private const val RETRY_EXHAUSTED_FALLBACK_HINT =
        "Check your API key, base URL, and account credits in Settings, then try again."
    private const val MAX_CAUSE_CHAIN_DEPTH = 32

    private val PREFIX_PATTERNS =
        listOf(
            Regex("""(?i)^sorry, something went wrong\. please try again\.\s*"""),
            Regex("""(?i)^error:\s*"""),
            Regex("""(?i)^agent loop failed:\s*"""),
            Regex("""(?i)^completeSingleTurn failed:\s*"""),
            Regex("""(?i)^model turn failed \((?:transient|permanent)\):\s*"""),
        )
    private val MODEL_API_UNAVAILABLE =
        Regex("""(?i)model api unavailable:\s*(.+?)(?:\s*\(attempts=\d+\))?$""")
    private val GUARDRAIL_RETRY_EXHAUSTED =
        Regex("""(?i)guardrail exceeded:\s*model_api_retries_exhausted:\s*(.+)$""")
    private val HTTP_STATUS_PATTERNS =
        listOf(
            Regex("""(?i)\bstatus=(\d{3})\b"""),
            Regex("""(?i)\bHTTP\s+(\d{3})\b"""),
            Regex("""(?i)\bcode[=:]\s*(\d{3})\b"""),
            Regex("""\b(\d{3}):\s"""),
        )
    private val BODY_MESSAGE = Regex("""(?i)message=([^,}]+)""")

    fun map(error: Throwable): ChatInferenceUserError {
        when (error) {
            is InferenceBusyException ->
                return ChatInferenceUserError(
                    title =
                        ChatInferenceUserErrorSanitizer.sanitizeUserFacingTitle(
                            candidate = error.message,
                            fallback = BUSY_TITLE,
                        ),
                    category = ChatInferenceErrorCategory.BUSY,
                )
            is ActiveModelConfigurationDecryptException ->
                return ChatInferenceUserError(
                    title =
                        ChatInferenceUserErrorSanitizer.sanitizeUserFacingTitle(
                            candidate = error.message,
                            fallback = ActiveModelConfigurationDecryptException.USER_MESSAGE,
                        ),
                    category = ChatInferenceErrorCategory.DECRYPT_FAILURE,
                )
        }

        val rawMessage = collectMessages(error)
        return mapFromMessage(rawMessage)
    }

    fun mapFromMessage(rawMessage: String?): ChatInferenceUserError {
        val httpStatusFromRaw = rawMessage?.let(::extractHttpStatus)
        val providerDetailFromRaw =
            rawMessage?.let { raw ->
                extractProviderDetail(raw, httpStatusFromRaw ?: extractHttpStatus(raw))
            }
        val normalized = normalizeMessage(rawMessage)
        if (normalized.isBlank()) {
            return genericError()
        }

        val specialized =
            sequenceOf(
                mapPromptTooLarge(normalized),
                mapGuardrailExceeded(normalized, httpStatusFromRaw, providerDetailFromRaw),
                mapModelApiUnavailable(normalized, httpStatusFromRaw, providerDetailFromRaw),
                mapModelStopped(normalized),
                mapStreamTermination(normalized),
                mapAgentInternal(normalized),
            ).firstNotNullOfOrNull { it }

        if (specialized != null) {
            return specialized
        }

        val httpStatus = httpStatusFromRaw ?: extractHttpStatus(normalized)
        val providerDetail =
            providerDetailFromRaw ?: extractProviderDetail(normalized, httpStatus)
        return mapHttpStatus(httpStatus, providerDetail)
            ?: providerDetail?.let { detail ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.GENERIC,
                    providerDetail = detail,
                    defaultTitle = GENERIC_TITLE,
                    hint = GENERIC_HINT,
                )
            }
            ?: genericError()
    }

    fun isPromptTooLarge(source: String): Boolean =
        source.contains(PROMPT_TOO_LARGE_TITLE, ignoreCase = false) ||
            mapFromMessage(source).category == ChatInferenceErrorCategory.PROMPT_TOO_LARGE

    private fun genericError(): ChatInferenceUserError =
        ChatInferenceUserError(
            title = GENERIC_TITLE,
            hint = GENERIC_HINT,
        )

    private fun providerFirstError(
        category: ChatInferenceErrorCategory,
        providerDetail: String?,
        defaultTitle: String,
        hint: String?,
    ): ChatInferenceUserError {
        val sanitized = providerDetail?.let(ChatInferenceUserErrorSanitizer::sanitizeProviderDetail)
        val title = sanitized?.takeIf { it.isNotBlank() } ?: defaultTitle
        val redundantDetail = sanitized?.takeIf { it.isNotBlank() && it != title }
        return ChatInferenceUserError(
            title = title,
            detail = redundantDetail,
            hint = hint,
            category = category,
        )
    }

    private fun collectMessages(error: Throwable): String {
        val seen = mutableSetOf<Throwable>(error)
        val messages =
            generateSequence(error) { current ->
                current.cause?.takeIf { it !== current && seen.add(it) }
            }.take(MAX_CAUSE_CHAIN_DEPTH)
                .mapNotNull { throwable ->
                    throwable.message?.trim()?.takeIf { it.isNotBlank() }
                }.toList()

        return messages.maxByOrNull { scoreMessage(it) } ?: error.message.orEmpty()
    }

    private fun scoreMessage(message: String): Int {
        val normalized = normalizeMessage(message)
        var score = normalized.length
        if (normalized.contains("limit", ignoreCase = true)) score += 50
        if (normalized.contains("reset at", ignoreCase = true)) score += 50
        if (ChatInferenceUserErrorSanitizer.looksLikeStackTrace(normalized)) score -= 1_000
        if (normalized.startsWith("exception=", ignoreCase = true)) score -= 100
        return score
    }

    private fun normalizeMessage(rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) return ""

        val withoutStackTrace =
            rawMessage
                .lineSequence()
                .takeWhile { line ->
                    !ChatInferenceSdkMetadata.STACK_TRACE_LINE.containsMatchIn(line) &&
                        !line.contains("Caused by:")
                }.joinToString("\n")
                .trim()

        var message = withoutStackTrace
        PREFIX_PATTERNS.forEach { pattern ->
            message = message.replace(pattern, "").trim()
        }

        return message
            .replace(ChatInferenceSdkMetadata.EXCEPTION_MESSAGE_PREFIX, "")
            .replace(ChatInferenceSdkMetadata.STATUS_SUFFIX_INLINE, "")
            .trim()
    }

    private fun mapPromptTooLarge(normalized: String): ChatInferenceUserError? {
        val isPipelineCeiling =
            normalized.contains("tier=pipeline", ignoreCase = true) &&
                normalized.contains("exceeds hard ceiling", ignoreCase = true)
        val isCompactionCeiling =
            normalized.contains("compaction failed", ignoreCase = true) &&
                (
                    normalized.contains("exceeds hard ceiling", ignoreCase = true) ||
                        normalized.contains("projected prompt", ignoreCase = true)
                )
        if (!isPipelineCeiling && !isCompactionCeiling) {
            return null
        }
        return ChatInferenceUserError(
            title = PROMPT_TOO_LARGE_TITLE,
            hint = "Shorten your message or start a new chat, then try again.",
            category = ChatInferenceErrorCategory.PROMPT_TOO_LARGE,
        )
    }

    private fun mapGuardrailExceeded(
        normalized: String,
        httpStatusFromRaw: Int?,
        providerDetailFromRaw: String?,
    ): ChatInferenceUserError? {
        if (!normalized.contains("guardrail exceeded", ignoreCase = true)) {
            return null
        }

        return when {
            normalized.contains("model_api_retries_exhausted", ignoreCase = true) ->
                mapRetryExhaustedGuardrail(normalized, httpStatusFromRaw, providerDetailFromRaw)
            normalized.contains("max_tool_turns", ignoreCase = true) ||
                normalized.contains("max_turns", ignoreCase = true) ||
                normalized.contains("max_model_requests_per_turn", ignoreCase = true) ->
                ChatInferenceUserError(
                    title = "This task ran longer than the agent can handle in one turn.",
                    hint = "Try breaking the request into smaller steps.",
                    category = ChatInferenceErrorCategory.AGENT_LIMIT,
                )
            else -> null
        }
    }

    private fun mapRetryExhaustedGuardrail(
        normalized: String,
        httpStatusFromRaw: Int?,
        providerDetailFromRaw: String?,
    ): ChatInferenceUserError {
        val embeddedMessage =
            GUARDRAIL_RETRY_EXHAUSTED
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (embeddedMessage != null) {
            val httpStatus = httpStatusFromRaw ?: extractHttpStatus(embeddedMessage)
            val providerDetail =
                providerDetailFromRaw ?: extractProviderDetail(embeddedMessage, httpStatus)
            mapHttpStatus(httpStatus, providerDetail)?.let { return it }
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(embeddedMessage)?.let { sanitized ->
                val category =
                    if (isRateLimitedDetail(httpStatus, sanitized)) {
                        ChatInferenceErrorCategory.RATE_LIMITED
                    } else {
                        ChatInferenceErrorCategory.PROVIDER_UNAVAILABLE
                    }
                val hint =
                    if (category == ChatInferenceErrorCategory.RATE_LIMITED) {
                        RATE_LIMIT_HINT
                    } else {
                        RETRY_EXHAUSTED_FALLBACK_HINT
                    }
                return providerFirstError(
                    category = category,
                    providerDetail = embeddedMessage,
                    defaultTitle = RETRY_EXHAUSTED_FALLBACK_TITLE,
                    hint = hint,
                )
            }
        }

        return ChatInferenceUserError(
            title = RETRY_EXHAUSTED_FALLBACK_TITLE,
            hint = RETRY_EXHAUSTED_FALLBACK_HINT,
            category = ChatInferenceErrorCategory.PROVIDER_UNAVAILABLE,
        )
    }

    private fun isRateLimitedDetail(
        httpStatus: Int?,
        sanitized: String,
    ): Boolean {
        val normalizedDetail = sanitized.lowercase()
        return httpStatus == ModelApiTransportHeuristics.HTTP_TOO_MANY_REQUESTS ||
            normalizedDetail.contains("rate limit") ||
            normalizedDetail.contains("too many requests") ||
            normalizedDetail.contains("limit exhausted") ||
            normalizedDetail.contains("limit will reset")
    }

    private fun mapModelApiUnavailable(
        normalized: String,
        httpStatusFromRaw: Int?,
        providerDetailFromRaw: String?,
    ): ChatInferenceUserError? {
        val match = MODEL_API_UNAVAILABLE.find(normalized) ?: return null

        val innerMessage = match.groupValues[1].trim()
        val innerHttpStatus = httpStatusFromRaw ?: extractHttpStatus(innerMessage)
        val providerDetail =
            providerDetailFromRaw ?: extractProviderDetail(innerMessage, innerHttpStatus)
        mapHttpStatus(innerHttpStatus, providerDetail)?.let { return it }

        val transientStreamFailure =
            ModelApiTransportHeuristics.isRecoverableStreamTermination(innerMessage) ||
                ModelApiTransportHeuristics.isTransientTransportMessage(innerMessage.lowercase())

        return if (transientStreamFailure) {
            ChatInferenceUserError(
                title = "Your model provider is temporarily unavailable.",
                hint = "Try again in a few moments.",
                category = ChatInferenceErrorCategory.TRANSPORT_FAILURE,
            )
        } else {
            providerFirstError(
                category = ChatInferenceErrorCategory.PROVIDER_UNAVAILABLE,
                providerDetail = innerMessage,
                defaultTitle = "The model provider is unavailable.",
                hint = RETRY_EXHAUSTED_FALLBACK_HINT,
            )
        }
    }

    private fun mapStreamTermination(normalized: String): ChatInferenceUserError? {
        if (extractHttpStatus(normalized) != null) {
            return null
        }
        val isStreamFailure =
            normalized.contains("stream ended unexpectedly", ignoreCase = true) ||
                normalized.contains("model stream ended unexpectedly", ignoreCase = true) ||
                ModelApiTransportHeuristics.isRecoverableStreamTermination(normalized)
        if (!isStreamFailure) {
            return null
        }
        return ChatInferenceUserError(
            title = "Your model provider is temporarily unavailable.",
            hint = "Try again in a few moments.",
            category = ChatInferenceErrorCategory.TRANSPORT_FAILURE,
        )
    }

    private fun mapAgentInternal(normalized: String): ChatInferenceUserError? {
        if (!ChatInferenceUserErrorSanitizer.looksLikeAgentInternalMessage(normalized)) {
            return null
        }
        return ChatInferenceUserError(
            title = GENERIC_TITLE,
            hint = GENERIC_HINT,
            category = ChatInferenceErrorCategory.AGENT_INTERNAL,
        )
    }

    private fun mapModelStopped(normalized: String): ChatInferenceUserError? {
        if (!normalized.contains("model stopped before completion", ignoreCase = true)) {
            return null
        }
        return if (normalized.contains("max_tokens", ignoreCase = true)) {
            ChatInferenceUserError(
                title = "The response hit the model token limit.",
                hint = "Shorten your prompt or start a new message and try again.",
                category = ChatInferenceErrorCategory.TOKEN_LIMIT,
            )
        } else {
            ChatInferenceUserError(
                title = "The model stopped before finishing its response.",
                hint = GENERIC_HINT,
                category = ChatInferenceErrorCategory.GENERIC,
            )
        }
    }

    private fun mapHttpStatus(
        httpStatus: Int?,
        providerDetail: String?,
    ): ChatInferenceUserError? =
        when (httpStatus) {
            ModelApiTransportHeuristics.HTTP_UNAUTHORIZED ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.AUTH_FAILURE,
                    providerDetail = providerDetail,
                    defaultTitle = "Authentication failed with your model provider.",
                    hint = "Check your API key in Settings and try again.",
                )
            ModelApiTransportHeuristics.HTTP_FORBIDDEN ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.ACCESS_DENIED,
                    providerDetail = providerDetail,
                    defaultTitle = "Access denied by your model provider.",
                    hint = "Your API key may not have permission for this model.",
                )
            ModelApiTransportHeuristics.HTTP_NOT_FOUND ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.ENDPOINT_NOT_FOUND,
                    providerDetail = providerDetail,
                    defaultTitle = "The model endpoint was not found.",
                    hint = "Check the Base URL in your provider settings.",
                )
            ModelApiTransportHeuristics.HTTP_TOO_MANY_REQUESTS ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.RATE_LIMITED,
                    providerDetail = providerDetail,
                    defaultTitle = "Rate limit exceeded.",
                    hint = RATE_LIMIT_HINT,
                )
            ModelApiTransportHeuristics.HTTP_REQUEST_TIMEOUT,
            ModelApiTransportHeuristics.HTTP_BAD_GATEWAY,
            ModelApiTransportHeuristics.HTTP_SERVICE_UNAVAILABLE,
            ModelApiTransportHeuristics.HTTP_GATEWAY_TIMEOUT,
            ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.TRANSPORT_FAILURE,
                    providerDetail = providerDetail,
                    defaultTitle = "Your model provider is temporarily unavailable.",
                    hint = "Try again in a few moments.",
                )
            ModelApiTransportHeuristics.HTTP_BAD_REQUEST ->
                providerFirstError(
                    category = ChatInferenceErrorCategory.INVALID_REQUEST,
                    providerDetail = providerDetail,
                    defaultTitle = "The model provider rejected this request.",
                    hint = "Check the model name and your recent conversation, then try again.",
                )
            else -> null
        }

    private fun extractHttpStatus(message: String): Int? =
        HTTP_STATUS_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern
                .find(message)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it in ModelApiTransportHeuristics.HTTP_STATUS_MIN..ModelApiTransportHeuristics.HTTP_STATUS_MAX }
        }

    private fun extractProviderDetail(
        message: String,
        httpStatus: Int?,
    ): String? {
        val bodyMessage =
            BODY_MESSAGE
                .findAll(message)
                .map { it.groupValues[1].trim() }
                .lastOrNull()
        if (bodyMessage != null) {
            val sanitized = ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(bodyMessage)
            if (sanitized != null) return sanitized
        }

        if (httpStatus == null) {
            return ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(message)
        }

        val afterStatus =
            Regex("""(?i)\b$httpStatus:\s*(.+)$""")
                .find(message)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        return ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(afterStatus)
            ?: ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(message)
    }
}
