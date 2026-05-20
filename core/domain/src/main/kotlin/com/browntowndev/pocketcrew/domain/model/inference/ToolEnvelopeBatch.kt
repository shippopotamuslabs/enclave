package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Pure-domain projection of the Rust `tool_parse::result::ToolParseBatch`.
 *
 * The Rust side returns FFI types; the `:core:agent` adapter maps them to
 * these pure-Kotlin records so that `:core:domain` stays free of UniFFI /
 * Android dependencies (per `AGENTS.md` §2.1).
 */
data class ToolEnvelopeBatch(
    val successes: List<ToolEnvelopeField>,
    val failures: List<ToolEnvelopeFailure>,
    val visiblePrefix: String,
    val visibleSuffix: String,
)

data class ToolEnvelopeField(
    val toolName: String,
    val argumentsJson: String,
    val repaired: Boolean,
    val repairs: List<String>,
)

data class ToolEnvelopeFailure(
    val reason: ToolEnvelopeFailureReason,
    val original: String,
    /**
     * Optional format hint used by the facade to format DSML-specific
     * exception messages (`"Malformed DSML tool envelope"` vs
     * `"Malformed tool_call envelope"`).
     *
     * Carried as a plain string instead of an enum so the FFI surface can
     * pass `Some("dsml")` without bringing a new UniFFI enum across the
     * boundary.
     */
    val formatHint: String? = null,
)

sealed class ToolEnvelopeFailureReason {
    object MalformedEnvelope : ToolEnvelopeFailureReason()

    data class InvalidJson(
        val detail: String,
    ) : ToolEnvelopeFailureReason()

    data class UnknownTool(
        val name: String,
    ) : ToolEnvelopeFailureReason()

    data class UnknownParameter(
        val tool: String,
        val param: String,
    ) : ToolEnvelopeFailureReason()

    data class DuplicateParameter(
        val tool: String,
        val param: String,
    ) : ToolEnvelopeFailureReason()

    data class MissingRequiredParameter(
        val tool: String,
        val param: String,
    ) : ToolEnvelopeFailureReason()

    data class SchemaCoercionFailed(
        val tool: String,
        val param: String,
        val expected: String,
    ) : ToolEnvelopeFailureReason()
}
