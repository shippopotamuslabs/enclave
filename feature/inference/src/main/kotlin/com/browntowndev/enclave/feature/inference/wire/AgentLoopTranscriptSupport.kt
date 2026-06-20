package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper

/**
 * Shared transcript-shape helpers for the Rust agent loop.
 *
 * Replaces the inline `indexOfLast { ... call_id == null }` and
 * `indices.lastOrNull { !isEngineInternalMessage(...) }` copies that drifted across
 * [com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService],
 * [com.browntowndev.enclave.feature.inference.AnthropicInferenceServiceImpl],
 * [com.browntowndev.enclave.feature.inference.GoogleAgentLoopContentMapper], and the
 * append-only placement strategies.
 */

/** Id prefix for compacted-summary rows injected by the Rust compaction pipeline. */
const val COMPACT_SUMMARY_ID_PREFIX = "compact-summary-"

/** Wire label prepended to compacted-summary content on every provider. */
const val COMPACT_SUMMARY_WIRE_LABEL = "[Conversation summary]"

/** True for compaction-summary rows produced by the Rust engine. */
fun AgentLoopMessage.isCompactSummary(): Boolean = id.startsWith(COMPACT_SUMMARY_ID_PREFIX)

/** Renders a compact-summary row's content with the shared wire label. */
fun formatCompactSummary(content: String): String = "$COMPACT_SUMMARY_WIRE_LABEL\n$content"

/**
 * Index of the last `role: user` row that is not a tool-result (`call_id`) row.
 *
 * Tool-result rows are delivered as `role: user` with a `call_id` metadata key; they are not
 * real user prompts and must not be selected as the image-attachment or sidecar-tail anchor.
 * Returns -1 when no such row exists.
 */
fun List<AgentLoopMessage>.lastNonToolResultUserIndex(): Int =
    indexOfLast { message ->
        message.role == Role.USER && message.metadata().toolCallId == null
    }

/**
 * Index of the last session row that is not engine-internal (budget, nudges, envelope
 * failures). Used as the sidecar tail anchor so volatile context is not pushed behind
 * engine-internal payloads. Returns 0 for an empty transcript.
 */
fun List<AgentLoopMessage>.lastNonEngineInternalIndex(): Int {
    if (isEmpty()) return 0
    return indices.lastOrNull { index ->
        !EngineInternalMessageMapper.isEngineInternalMessage(this[index].metadataJson)
    } ?: 0
}
