package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.port.inference.LoggingPort

/**
 * Stream-time size guards for OpenAI-SDK-shaped chat completions.
 *
 * Pure helpers driven by [BaseOpenAiSdkInferenceService.streamChatCompletions]. Stops a runaway
 * provider from filling the JVM heap with assistant text or tool-call argument strings before
 * the model turn result is handed back across the FFI boundary.
 *
 * Caps are tuned so that:
 *   - conversational replies cap out at ~1MB chars (well above any legitimate chat bubble),
 *   - tool call argument JSON caps at ~10MB chars (covers worst-case structured artifact bodies),
 *   - any single provider delta chunk over 256KB chars is dropped as misbehavior.
 */
internal object OpenAiSdkStreamCaps {
    const val MAX_ASSISTANT_TEXT_CHARS = 1_048_576
    const val MAX_TOOL_ARGS_CHARS = 10_485_760
    const val MAX_DELTA_CHUNK_CHARS = 262_144
    const val ASSISTANT_TEXT_TRUNCATION_MARKER =
        "\n\n[truncated: model output exceeded client limit; remainder discarded]"
    const val TOOL_ARGS_TRUNCATED_PAYLOAD =
        """{"_client_error":"tool_arguments_exceeded_max_bytes","_max_bytes":10485760}"""

    /** True when a single provider text delta is over the per-chunk cap and should be discarded. */
    fun isDeltaChunkOversize(chunkLen: Int): Boolean = chunkLen > MAX_DELTA_CHUNK_CHARS

    /**
     * Returns true if [builder] now exceeds the assistant-text cap, in which case the buffer is
     * truncated in place and the marker appended. Caller should treat this as "stream finished
     * early" and break the read loop.
     */
    fun applyAssistantTextCap(
        builder: StringBuilder,
        loggingPort: LoggingPort? = null,
        tag: String? = null,
        modelId: String? = null,
    ): Boolean {
        if (builder.length <= MAX_ASSISTANT_TEXT_CHARS) return false
        val originalLen = builder.length
        builder.setLength(MAX_ASSISTANT_TEXT_CHARS)
        builder.append(ASSISTANT_TEXT_TRUNCATION_MARKER)
        if (loggingPort != null && tag != null) {
            loggingPort.warning(
                tag,
                "Capping assistantText at $MAX_ASSISTANT_TEXT_CHARS chars model=${modelId ?: "<unknown>"} " +
                    "actual=$originalLen; ending stream early",
            )
        }
        return true
    }

    /**
     * Attempts to append [chunk] to [builder] (which currently holds a tool call's arguments JSON).
     * Returns true if appending was rejected because the cap would have been exceeded. The caller
     * should set its "args truncated" flag once this returns true and discard any further chunks
     * for that tool call.
     */
    fun appendToolArgsChunkOrReject(
        builder: StringBuilder,
        chunk: String,
        toolName: String?,
        loggingPort: LoggingPort? = null,
        tag: String? = null,
        modelId: String? = null,
    ): Boolean {
        if (builder.length.toLong() + chunk.length > MAX_TOOL_ARGS_CHARS) {
            if (loggingPort != null && tag != null) {
                loggingPort.warning(
                    tag,
                    "Capping tool call args model=${modelId ?: "<unknown>"} " +
                        "toolName=${toolName ?: "<unknown>"} " +
                        "accumulated=${builder.length} attemptedChunk=${chunk.length} max=$MAX_TOOL_ARGS_CHARS",
                )
            }
            return true
        }
        builder.append(chunk)
        return false
    }
}
