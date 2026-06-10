package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.port.inference.LoggingPort

/**
 * Defense-in-depth size guard applied just before lowering a model turn result across the UniFFI
 * boundary. Stops a runaway model output from triggering JVM OOM during UTF-8 encoding on the
 * Rust side of the call.
 */
internal object FfiPayloadGuard {
    const val MAX_ASSISTANT_TEXT_CHARS = 1_048_576
    const val MAX_TOOL_ARGS_CHARS = 10_485_760
    const val ASSISTANT_TEXT_TRUNCATION_MARKER =
        "\n\n[truncated by FFI guard: assistant text exceeded limit]"
    const val TOOL_ARGS_TRUNCATED_PAYLOAD =
        """{"_client_error":"tool_arguments_exceeded_ffi_limit","_max_bytes":10485760}"""
    private const val TAG = "RustAgentEngineAdapter"

    fun capAssistantText(
        text: String?,
        loggingPort: LoggingPort? = null,
    ): String? {
        if (text == null) return null
        if (text.length <= MAX_ASSISTANT_TEXT_CHARS) return text
        loggingPort?.warning(
            TAG,
            "FFI guard truncating assistantText originalChars=${text.length} max=$MAX_ASSISTANT_TEXT_CHARS",
        )
        // Back off one char if the cut would strand a high surrogate (the lone
        // surrogate would become U+FFFD during UTF-8 encoding at the FFI).
        val end =
            if (text[MAX_ASSISTANT_TEXT_CHARS - 1].isHighSurrogate()) {
                MAX_ASSISTANT_TEXT_CHARS - 1
            } else {
                MAX_ASSISTANT_TEXT_CHARS
            }
        return text.substring(0, end) + ASSISTANT_TEXT_TRUNCATION_MARKER
    }

    fun capToolArgs(
        argumentsJson: String,
        toolName: String?,
        loggingPort: LoggingPort? = null,
    ): String {
        if (argumentsJson.length <= MAX_TOOL_ARGS_CHARS) return argumentsJson
        loggingPort?.warning(
            TAG,
            "FFI guard replacing oversized tool args toolName=${toolName ?: "<unknown>"} " +
                "originalChars=${argumentsJson.length} max=$MAX_TOOL_ARGS_CHARS",
        )
        return TOOL_ARGS_TRUNCATED_PAYLOAD
    }
}
