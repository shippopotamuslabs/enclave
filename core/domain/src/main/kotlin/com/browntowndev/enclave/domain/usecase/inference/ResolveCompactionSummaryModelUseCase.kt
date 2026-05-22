package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.util.TokenCounter

/**
 * Chooses which [ModelType] should run Rust agent-loop summary compaction.
 *
 * FAST is used only when it is a hosted (non-local) model whose context window can fit the
 * full summarize payload plus the summary output reserve. Otherwise the active turn's model
 * is used so we do not spin up a second on-device runtime (e.g. LiteRT for FAST while THINKING
 * is already loaded).
 */
class ResolveCompactionSummaryModelUseCase(
    private val activeModelProvider: ActiveModelProviderPort,
    private val tokenCounter: TokenCounter,
) {
    suspend fun invoke(
        activeTurnModelType: ModelType,
        messagesToSummarize: List<ChatMessage>,
        summaryOutputTokenReserve: Int = DEFAULT_SUMMARY_OUTPUT_TOKEN_RESERVE,
    ): ModelType {
        val requiredContextTokens =
            messagesToSummarize.sumOf { tokenCounter.countTokens(it.content) } +
                summaryOutputTokenReserve

        val fastConfig = activeModelProvider.getActiveConfiguration(ModelType.FAST)
        if (fastConfig != null && !fastConfig.isLocal) {
            val fastContextWindow = fastConfig.contextWindow
            if (fastContextWindow != null && fastContextWindow >= requiredContextTokens) {
                return ModelType.FAST
            }
        }

        return activeTurnModelType
    }

    private companion object {
        const val DEFAULT_SUMMARY_OUTPUT_TOKEN_RESERVE = 1024
    }
}
