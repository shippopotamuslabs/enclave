package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeField
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort

/**
 * Pure-Kotlin Fake implementing [ToolEnvelopeParserPort] per the
 * "Fakes over Mocks" rule in `contracts/UNIT_TESTING.md`.
 *
 * Each method records its inputs and returns a programmable canned response.
 * Used by both the K-XX scenarios in `ToolEnvelopeParserTest` and the LI-XX
 * scenarios in `:feature:inference` tests.
 */
class FakeToolEnvelopeParserPort : ToolEnvelopeParserPort {
    val parseCalls: MutableList<String> = mutableListOf()
    val coerceProviderArgumentsCalls: MutableList<Pair<String, String>> = mutableListOf()
    val hasLocalToolContractCalls: MutableList<String?> = mutableListOf()
    val buildToolResultMessageCalls: MutableList<String> = mutableListOf()

    /** Canned [ToolEnvelopeBatch] responses keyed by raw text. */
    val parseResponses: MutableMap<String, ToolEnvelopeBatch> = mutableMapOf()

    /** Default response when [parseResponses] has no entry for the raw text. */
    var defaultParseResponse: ToolEnvelopeBatch =
        ToolEnvelopeBatch(
            successes = emptyList(),
            failures = emptyList(),
            visiblePrefix = "",
            visibleSuffix = "",
        )

    /** Canned [ToolEnvelopeField] responses keyed by (toolName, argsJson). */
    val coerceProviderArgumentsResponses: MutableMap<Pair<String, String>, ToolEnvelopeField> =
        mutableMapOf()

    /** Canned [Boolean] responses keyed by system prompt (nullable). */
    val hasLocalToolContractResponses: MutableMap<String?, Boolean> = mutableMapOf()

    /** Optional override for [buildToolResultMessage]; defaults to the existing wrapper. */
    var buildToolResultMessageImpl: (String) -> String = { "<tool_result>$it</tool_result>" }

    override fun parse(
        raw: String,
        toolSemanticsJson: String,
        toolCatalogJson: String,
    ): ToolEnvelopeBatch {
        parseCalls.add(raw)
        return parseResponses[raw] ?: defaultParseResponse
    }

    override fun coerceProviderArguments(
        toolName: String,
        argsJson: String,
        toolSemanticsJson: String,
        toolCatalogJson: String,
    ): ToolEnvelopeField {
        coerceProviderArgumentsCalls.add(toolName to argsJson)
        return coerceProviderArgumentsResponses[toolName to argsJson]
            ?: ToolEnvelopeField(
                toolName = toolName,
                argumentsJson = argsJson,
                repaired = false,
                repairs = emptyList(),
            )
    }

    override fun hasLocalToolContract(
        systemPrompt: String?,
        toolCatalogJson: String,
    ): Boolean {
        hasLocalToolContractCalls.add(systemPrompt)
        return hasLocalToolContractResponses[systemPrompt] ?: (
            systemPrompt != null &&
                (
                    systemPrompt.contains("<tool_call>") ||
                        systemPrompt.contains("{\"name\"") ||
                        systemPrompt.contains("<![CDATA[<tool") ||
                        systemPrompt.contains("DSML") ||
                        systemPrompt.contains("invoke name=")
                ) &&
                listOf(
                    "web_search",
                    "web_extract",
                    "attached_image_inspect",
                    "search_chat_history",
                    "search_chat",
                    "get_message_context",
                    "search_x_recent",
                    "generate_image",
                    "generate_video",
                    "generate_music",
                ).any { systemPrompt.contains(it) }
        )
    }

    override fun buildToolResultMessage(resultJson: String): String {
        buildToolResultMessageCalls.add(resultJson)
        return buildToolResultMessageImpl(resultJson)
    }
}
