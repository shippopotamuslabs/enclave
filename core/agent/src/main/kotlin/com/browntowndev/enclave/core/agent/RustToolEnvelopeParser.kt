package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeField
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import javax.inject.Inject
import javax.inject.Singleton

private fun rustParseToolCalls(
    raw: String,
    toolSemanticsJson: String,
    toolCatalogJson: String,
): FfiToolParseBatch = parseToolCalls(raw, toolSemanticsJson, toolCatalogJson)

private fun rustCoerceProviderArguments(
    toolName: String,
    argsJson: String,
    toolSemanticsJson: String,
    toolCatalogJson: String,
): FfiToolParseField = coerceProviderArguments(toolName, argsJson, toolSemanticsJson, toolCatalogJson)

private fun rustHasLocalToolContract(
    systemPrompt: String,
    toolCatalogJson: String,
): Boolean = hasLocalToolContract(systemPrompt, toolCatalogJson)

/**
 * Hilt-injected adapter wiring [ToolEnvelopeParserPort] to the UniFFI-exported
 * Rust `tool_parse` pipeline in `agent_core`.
 */
@Singleton
class RustToolEnvelopeParser
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
    ) : ToolEnvelopeParserPort {
        internal var parseToolCallsDelegate: (String, String, String) -> FfiToolParseBatch = ::rustParseToolCalls
        internal var coerceProviderArgumentsDelegate: (String, String, String, String) -> FfiToolParseField =
            ::rustCoerceProviderArguments
        internal var hasLocalToolContractDelegate: (String, String) -> Boolean = ::rustHasLocalToolContract

        override fun parse(
            raw: String,
            toolSemanticsJson: String,
            toolCatalogJson: String,
        ): ToolEnvelopeBatch =
            runCatching { parseToolCallsDelegate(raw, toolSemanticsJson, toolCatalogJson).toDomain() }
                .onSuccess { batch ->
                    if (batch.failures.isNotEmpty()) {
                        batch.failures.forEach { failure ->
                            loggingPort.warning(
                                "RustToolEnvelopeParser",
                                "Tool call envelope parsing failure: reason=${failure.reason} original=${failure.original}"
                            )
                        }
                    }
                }
                .getOrElse { error ->
                    loggingPort.error("RustToolEnvelopeParser", "Failed to parse tool calls. raw=$raw", error)
                    throw IllegalStateException("Rust tool parser unavailable", error)
                }

        override fun coerceProviderArguments(
            toolName: String,
            argsJson: String,
            toolSemanticsJson: String,
            toolCatalogJson: String,
        ): ToolEnvelopeField =
            runCatching {
                coerceProviderArgumentsDelegate(toolName, argsJson, toolSemanticsJson, toolCatalogJson).toDomain()
            }.getOrElse { error ->
                loggingPort.error(
                    "RustToolEnvelopeParser",
                    "Failed to coerce provider arguments toolName=$toolName argsJson=$argsJson",
                    error
                )
                throw IllegalStateException("Rust tool parser unavailable", error)
            }

        override fun hasLocalToolContract(
            systemPrompt: String?,
            toolCatalogJson: String,
        ): Boolean =
            runCatching { hasLocalToolContractDelegate(systemPrompt.orEmpty(), toolCatalogJson) }
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
