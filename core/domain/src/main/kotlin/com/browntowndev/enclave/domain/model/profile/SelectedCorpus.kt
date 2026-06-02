package com.browntowndev.enclave.domain.model.profile

/**
 * Result of the Phase-2 diversity + budget selection pass.
 *
 * [selectedConversationIds] are internal Room IDs from [ProfileCorpusConversationEntity].
 */
data class SelectedCorpus(
    val corpusRunId: String,
    val selectedConversationIds: List<String>,
    val totalConversationsInCorpus: Int,
    val estimatedInputTokens: Int,
    val coveragePercent: Float,
) {
    val selectionPercent: Float
        get() =
            if (totalConversationsInCorpus == 0) {
                0f
            } else {
                selectedConversationIds.size.toFloat() / totalConversationsInCorpus
            }
}
