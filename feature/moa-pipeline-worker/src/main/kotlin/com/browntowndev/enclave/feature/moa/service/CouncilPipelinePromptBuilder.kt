package com.browntowndev.enclave.feature.moa.service

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort

internal object CouncilPipelinePromptBuilder {
    private const val CHARS_PER_TOKEN = 3.5
    private const val CONTEXT_AVAILABLE_RATIO = 0.75
    private const val DRAFT_TOKEN_RATIO = 0.40
    private const val SYNTHESIS_CANDIDATE_RATIO = 0.74
    private const val FINAL_USER_PROMPT_RATIO = 0.20
    private const val REVIEW_AVAILABLE_RATIO = 0.80
    private const val REVIEW_USER_PROMPT_RATIO = 0.13
    private const val REVIEW_USER_SYSTEM_RATIO = 0.13
    private const val REVIEW_CANDIDATE_RATIO = 0.74

    suspend fun buildPromptForStep(
        state: PipelineState,
        activeModelProvider: ActiveModelProviderPort,
    ): String {
        val userPrompt = state.userMessage
        val modelType = CouncilPipelineStepSupport.getModelTypeForStep(state.currentStep)
        val config = activeModelProvider.getActiveConfiguration(modelType)
        val contextWindow = config?.contextWindow ?: 4096

        return when (state.currentStep) {
            PipelineStep.DRAFT_ONE -> buildAnalyticalDraftPrompt(userPrompt, config?.systemPrompt ?: "", contextWindow)
            PipelineStep.DRAFT_TWO -> buildCreativeDraftPrompt(userPrompt, config?.systemPrompt ?: "", contextWindow)
            PipelineStep.SYNTHESIS ->
                buildMainSynthesisPrompt(
                    userPrompt,
                    state.stepOutputs[PipelineStep.DRAFT_ONE] ?: "",
                    state.stepOutputs[PipelineStep.DRAFT_TWO] ?: "",
                    config?.systemPrompt ?: "",
                    contextWindow,
                )
            PipelineStep.FINAL -> {
                val userSystemPrompt = activeModelProvider.getActiveConfiguration(ModelType.FAST)?.systemPrompt ?: ""
                buildFinalReviewPrompt(
                    userPrompt,
                    state.stepOutputs[PipelineStep.SYNTHESIS] ?: "",
                    userSystemPrompt,
                    config?.systemPrompt ?: "",
                    contextWindow,
                )
            }
        }
    }

    suspend fun calculateMaxTokensForStep(
        step: PipelineStep,
        activeModelProvider: ActiveModelProviderPort,
    ): Int? =
        when (step) {
            PipelineStep.DRAFT_ONE, PipelineStep.DRAFT_TWO -> {
                val synthConfig =
                    activeModelProvider.getActiveConfiguration(
                        CouncilPipelineStepSupport.getModelTypeForStep(PipelineStep.SYNTHESIS),
                    )
                val synthContext = synthConfig?.contextWindow ?: 4096
                val synthSystemTokens = (synthConfig?.systemPrompt?.length ?: 0) / CHARS_PER_TOKEN
                val available = (synthContext * CONTEXT_AVAILABLE_RATIO) - synthSystemTokens
                (available * DRAFT_TOKEN_RATIO).toInt().coerceAtLeast(100)
            }
            PipelineStep.SYNTHESIS -> {
                val finalConfig =
                    activeModelProvider.getActiveConfiguration(
                        CouncilPipelineStepSupport.getModelTypeForStep(PipelineStep.FINAL),
                    )
                val finalContext = finalConfig?.contextWindow ?: 4096
                val finalSystemTokens = (finalConfig?.systemPrompt?.length ?: 0) / CHARS_PER_TOKEN
                val available = (finalContext * CONTEXT_AVAILABLE_RATIO) - finalSystemTokens
                (available * SYNTHESIS_CANDIDATE_RATIO).toInt().coerceAtLeast(100)
            }
            PipelineStep.FINAL -> null
        }

    private fun truncateChars(
        text: String,
        maxTokens: Int,
    ): String {
        val maxChars = (maxTokens * CHARS_PER_TOKEN).toInt()
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n... [truncated to fit context window]"
    }

    private fun buildAnalyticalDraftPrompt(
        userPrompt: String,
        systemPrompt: String,
        contextWindow: Int,
    ): String {
        val systemPromptTokens = (systemPrompt.length / CHARS_PER_TOKEN).toInt()
        val budget = (contextWindow * REVIEW_AVAILABLE_RATIO).toInt() - systemPromptTokens
        val safeUserPrompt = truncateChars(userPrompt, budget.coerceAtLeast(100))

        return """
TASK: COMPLEX_DRAFT_ANALYTICAL

USER_PROMPT:
$safeUserPrompt
            """.trimIndent()
    }

    private fun buildCreativeDraftPrompt(
        userPrompt: String,
        systemPrompt: String,
        contextWindow: Int,
    ): String {
        val systemPromptTokens = (systemPrompt.length / CHARS_PER_TOKEN).toInt()
        val budget = (contextWindow * REVIEW_AVAILABLE_RATIO).toInt() - systemPromptTokens
        val safeUserPrompt = truncateChars(userPrompt, budget.coerceAtLeast(100))

        return """
TASK: COMPLEX_DRAFT_CREATIVE

USER_PROMPT:
$safeUserPrompt
            """.trimIndent()
    }

    @Suppress("MaxLineLength")
    private fun buildMainSynthesisPrompt(
        userPrompt: String,
        draft1: String,
        draft2: String,
        modelSystemPrompt: String,
        contextWindow: Int,
    ): String {
        val systemPromptTokens = (modelSystemPrompt.length / CHARS_PER_TOKEN).toInt()
        val totalAvailable = (contextWindow * CONTEXT_AVAILABLE_RATIO).toInt() - systemPromptTokens

        val draftBudget = (totalAvailable * DRAFT_TOKEN_RATIO).toInt()
        val promptBudget = (totalAvailable * FINAL_USER_PROMPT_RATIO).toInt()

        val safeUserPrompt = truncateChars(userPrompt, promptBudget.coerceAtLeast(100))
        val safeDraft1 = truncateChars(draft1, draftBudget.coerceAtLeast(100))
        val safeDraft2 = truncateChars(draft2, draftBudget.coerceAtLeast(100))

        return """
TASK: COMPLEX_SYNTHESIZE

ORIGINAL_USER_PROMPT:
$safeUserPrompt

DRAFT_1:
$safeDraft1

DRAFT_2:
$safeDraft2
            """.trimIndent()
    }

    @Suppress("MaxLineLength")
    private fun buildFinalReviewPrompt(
        userPrompt: String,
        candidateAnswer: String,
        userSystemPrompt: String,
        modelSystemPrompt: String,
        contextWindow: Int,
    ): String {
        val modelSystemPromptTokens = (modelSystemPrompt.length / CHARS_PER_TOKEN).toInt()
        val totalAvailable = (contextWindow * CONTEXT_AVAILABLE_RATIO).toInt() - modelSystemPromptTokens

        val userPromptBudget = (totalAvailable * REVIEW_USER_PROMPT_RATIO).toInt()
        val userSystemBudget = (totalAvailable * REVIEW_USER_SYSTEM_RATIO).toInt()
        val candidateBudget = (totalAvailable * REVIEW_CANDIDATE_RATIO).toInt()

        val safeUserPrompt = truncateChars(userPrompt, userPromptBudget.coerceAtLeast(100))
        val safeCandidate = truncateChars(candidateAnswer, candidateBudget.coerceAtLeast(100))
        val safeUserSystemPrompt = truncateChars(userSystemPrompt, userSystemBudget.coerceAtLeast(100))

        return """
TASK: FINAL_REVIEW_AND_REPLY

ORIGINAL_USER_PROMPT:
$safeUserPrompt

CANDIDATE_ANSWER:
$safeCandidate

USER_SYSTEM_PROMPT:
${safeUserSystemPrompt.ifEmpty { "(none provided)" }}

OUTPUT_CONTRACT:
@Suppress("MaxLineLength")
Produce the final polished response for the user. Output ONLY the essay itself — no critique, no feedback, no suggestions, no headings. Just the clean final answer.
            """.trimIndent()
    }
}
