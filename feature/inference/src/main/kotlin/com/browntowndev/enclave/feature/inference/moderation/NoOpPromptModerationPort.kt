package com.browntowndev.enclave.feature.inference.moderation

import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores

/**
 * No-op implementation used in the normal (non-Play-Store) flavor where
 * text classifiers are excluded entirely. Returns empty scores so moderation
 * results are never blocked.
 */
object NoOpPromptModerationPort : PromptModerationPort {
    override suspend fun classify(prompt: String): PromptModerationScores =
        PromptModerationScores(scoresByLabel = emptyMap())
}
