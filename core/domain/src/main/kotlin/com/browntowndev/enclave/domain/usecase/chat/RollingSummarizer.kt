package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopCompactionTrigger
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.TokenCounter
import kotlinx.coroutines.flow.toList

// Called from RustLoopInferenceService.summarize via the AGENT-028 summary tier.
class RollingSummarizer(
    private val loggingPort: LoggingPort,
    private val tokenCounter: TokenCounter,
) {
    suspend fun summarize(
        messages: List<ChatMessage>,
        service: LlmInferencePort,
        instruction: String = "Please provide a comprehensive but concise summary of the conversation history.",
        focusInstruction: String? = null,
        historyRef: String? = null,
        stateLedgerSnapshotJson: String? = null,
    ): String? {
        if (messages.size < 4) return null // Need enough context to summarize

        // Slice oldest 50% by tokens (protecting index 0 System Prompt if it exists)
        val history = messages.filter { it.role != Role.SYSTEM }
        val totalTokens = history.sumOf { tokenCounter.countTokens(it.content) }
        val targetTokens = totalTokens / 2

        var accumulatedTokens = 0
        var splitIndex = 0
        for (i in history.indices) {
            accumulatedTokens += tokenCounter.countTokens(history[i].content)
            if (accumulatedTokens >= targetTokens) {
                splitIndex = i + 1
                break
            }
        }

        val sliceToSummarize = history.subList(0, splitIndex)
        if (sliceToSummarize.isEmpty()) return null

        val prompt =
            buildAgentSummaryPrompt(
                messages = sliceToSummarize,
                instruction = instruction,
                focusInstruction = focusInstruction,
                historyRef = historyRef,
                stateLedgerSnapshotJson = stateLedgerSnapshotJson,
                trigger = AgentLoopCompactionTrigger.PREFLIGHT,
            )

        return summarizePrompt(prompt, service)
    }

    suspend fun summarizeAgentRequest(
        messages: List<ChatMessage>,
        service: LlmInferencePort,
        instruction: String,
        focusInstruction: String?,
        historyRef: String?,
        stateLedgerSnapshotJson: String?,
        trigger: AgentLoopCompactionTrigger,
    ): String? {
        if (messages.isEmpty()) return null
        return summarizePrompt(
            buildAgentSummaryPrompt(
                messages = messages,
                instruction = instruction,
                focusInstruction = focusInstruction,
                historyRef = historyRef,
                stateLedgerSnapshotJson = stateLedgerSnapshotJson,
                trigger = trigger,
            ),
            service,
        )
    }

    fun buildAgentSummaryPrompt(
        messages: List<ChatMessage>,
        instruction: String,
        focusInstruction: String?,
        historyRef: String?,
        stateLedgerSnapshotJson: String?,
        trigger: AgentLoopCompactionTrigger,
    ): String {
        val summaryGuidance =
            buildString {
                appendLine(instruction)
                appendLine("Compaction trigger: ${trigger.name}")
                focusInstruction?.let { appendLine("Focus instruction: $it") }
                historyRef?.let { appendLine("History reference: $it") }
                stateLedgerSnapshotJson?.let { appendLine("State ledger snapshot: $it") }
            }.trim()

        return """
            $summaryGuidance
            This summary will be used as context for future turns.

            History to summarize:
            ${messages.joinToString("\n") { "${it.role.name}: ${it.content}" }}

            Summary:
            """.trimIndent()
    }

    suspend fun summarizePrompt(
        prompt: String,
        service: LlmInferencePort,
    ): String? =
        try {
            val responseFlow = service.sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, maxTokens = 1024))
            val result =
                responseFlow
                    .toList()
                    .filterIsInstance<com.browntowndev.enclave.domain.port.inference.InferenceEvent.PartialResponse>()
                    .joinToString("") { it.chunk }
            result.trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            loggingPort.error("RollingSummarizer", "Failed to generate summary: ${e.message}")
            null
        }
}
