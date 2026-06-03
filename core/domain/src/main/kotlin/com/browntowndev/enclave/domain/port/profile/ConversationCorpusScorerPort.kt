package com.browntowndev.enclave.domain.port.profile

import com.browntowndev.enclave.domain.model.profile.SelectedCorpus

interface ConversationCorpusScorerPort {
    /**
     * Score all conversations in [corpusRunId], then select a diverse, budget-constrained
     * subset. Updates signal_score and selected_for_analysis flags in the corpus store.
     *
     * @param tokenBudget Maximum user-turn token count for the selected set.
     */
    suspend fun scoreAndSelect(
        corpusRunId: String,
        tokenBudget: Int,
    ): SelectedCorpus
}
