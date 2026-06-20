package com.browntowndev.enclave.feature.inference.wire

/**
 * Shared logging helpers for inference provider services.
 *
 * Replaces the `MAX_LOG_BODY_CHARS = 4_000` + `truncateForLogs` copies duplicated across
 * [com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService],
 * [com.browntowndev.enclave.feature.inference.AnthropicInferenceServiceImpl], and
 * [com.browntowndev.enclave.feature.inference.GoogleInferenceServiceImpl], and the
 * `describeException` copies on the OpenAI base and Anthropic service.
 */

/** Maximum number of characters of a wire body or error body to emit to the log. */
const val MAX_LOG_BODY_CHARS: Int = 4_000

/** Truncates [value] to [MAX_LOG_BODY_CHARS], appending a sentinel when truncation occurs. */
fun truncateForLogs(value: String): String =
    if (value.length <= MAX_LOG_BODY_CHARS) {
        value
    } else {
        value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }

/**
 * Renders a throwable as `exception=<SimpleClassName> message=<message>` for structured logs.
 *
 * Matches the shape previously inlined in the Anthropic and Google services. The OpenAI base's
 * richer [com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService.describeException]
 * adds `OpenAIServiceException`-specific status/code/type/param/body fields and is kept as-is
 * on the base class because it depends on the OpenAI SDK types; this helper covers the
 * provider-agnostic baseline used by the other services.
 */
fun describeInferenceException(throwable: Throwable): String =
    buildString {
        append("exception=")
        append(throwable::class.java.simpleName)
        throwable.message?.takeIf { it.isNotBlank() }?.let {
            append(" message=")
            append(it)
        }
    }
