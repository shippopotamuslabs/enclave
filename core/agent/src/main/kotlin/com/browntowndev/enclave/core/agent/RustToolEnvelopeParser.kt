package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeField
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import javax.inject.Inject
import javax.inject.Singleton

private fun rustParseToolCalls(raw: String): FfiToolParseBatch = parseToolCalls(raw)

private fun rustCoerceProviderArguments(
    toolName: String,
    argsJson: String,
): FfiToolParseField = coerceProviderArguments(toolName, argsJson)

private fun rustHasLocalToolContract(systemPrompt: String): Boolean = hasLocalToolContract(systemPrompt)

/**
 * Hilt-injected adapter wiring [ToolEnvelopeParserPort] to the UniFFI-exported
 * Rust `tool_parse` pipeline in `agent_core`.
 */
@Singleton
class RustToolEnvelopeParser
    @Inject
    constructor() : ToolEnvelopeParserPort {
        internal var parseToolCallsDelegate: (String) -> FfiToolParseBatch = ::rustParseToolCalls
        internal var coerceProviderArgumentsDelegate: (String, String) -> FfiToolParseField = ::rustCoerceProviderArguments
        internal var hasLocalToolContractDelegate: (String) -> Boolean = ::rustHasLocalToolContract

        override fun parse(raw: String): ToolEnvelopeBatch =
            runCatching { parseToolCallsDelegate(raw).toDomain() }
                .getOrElse { throw IllegalStateException("Rust tool parser unavailable", it) }

        override fun coerceProviderArguments(
            toolName: String,
            argsJson: String,
        ): ToolEnvelopeField =
            runCatching { coerceProviderArgumentsDelegate(toolName, argsJson).toDomain() }
                .getOrElse { throw IllegalStateException("Rust tool parser unavailable", it) }

        override fun hasLocalToolContract(systemPrompt: String?): Boolean =
            runCatching { hasLocalToolContractDelegate(systemPrompt.orEmpty()) }
                .getOrElse { throw IllegalStateException("Rust tool parser unavailable", it) }

        override fun buildToolResultMessage(resultJson: String): String = "<tool_result>$resultJson</tool_result>"

        private fun FfiToolParseBatch.toDomain(): ToolEnvelopeBatch =
            ToolEnvelopeBatch(
                successes = successes.map { it.toDomain() },
                failures = failures.map { it.toDomain() },
                visiblePrefix = visiblePrefix,
                visibleSuffix = visibleSuffix,
            )

        private fun FfiToolParseField.toDomain(): ToolEnvelopeField =
            ToolEnvelopeField(
                toolName = toolName,
                argumentsJson = argumentsJson,
                repaired = repaired,
                repairs = repairs,
            )

        private fun FfiToolParseFailure.toDomain(): ToolEnvelopeFailure =
            ToolEnvelopeFailure(
                reason = reason.toDomain(),
                original = original,
                formatHint = formatHint,
            )

        private fun FfiToolParseFailureReason.toDomain(): ToolEnvelopeFailureReason =
            when (this) {
                FfiToolParseFailureReason.MalformedEnvelope ->
                    ToolEnvelopeFailureReason.MalformedEnvelope
                is FfiToolParseFailureReason.InvalidJson ->
                    ToolEnvelopeFailureReason.InvalidJson(detail = detail)
                is FfiToolParseFailureReason.UnknownTool ->
                    ToolEnvelopeFailureReason.UnknownTool(name = name)
                is FfiToolParseFailureReason.UnknownParameter ->
                    ToolEnvelopeFailureReason.UnknownParameter(tool = tool, param = param)
                is FfiToolParseFailureReason.DuplicateParameter ->
                    ToolEnvelopeFailureReason.DuplicateParameter(tool = tool, param = param)
                is FfiToolParseFailureReason.MissingRequiredParameter ->
                    ToolEnvelopeFailureReason.MissingRequiredParameter(tool = tool, param = param)
                is FfiToolParseFailureReason.SchemaCoercionFailed ->
                    ToolEnvelopeFailureReason.SchemaCoercionFailed(tool = tool, param = param, expected = expected)
            }
    }
