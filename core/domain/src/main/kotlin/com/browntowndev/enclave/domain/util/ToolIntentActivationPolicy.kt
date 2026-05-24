package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.repository.SettingsData

data class ToolIntentActivationDecision(
    val activateArtifactTools: Boolean,
    val activateSearchX: Boolean,
    val runWebPreRetrieval: Boolean,
)

object ToolIntentActivationPolicy {
    fun resolve(
        prompt: String,
        embedding: FloatArray?,
        settings: SettingsData,
        classifier: SearchIntentClassifierPort,
    ): ToolIntentActivationDecision {
        val activateArtifactTools = ArtifactIntentPolicy.needsArtifactTools(prompt)

        if (embedding == null || !settings.isWebSearchSkillConfigured()) {
            return ToolIntentActivationDecision(
                activateArtifactTools = activateArtifactTools,
                activateSearchX = false,
                runWebPreRetrieval = false,
            )
        }

        val heuristicNonSearch = SearchIntentPolicy.isHeuristicNonSearchPrompt(prompt)
        val researchBypass = SearchIntentPolicy.hasConcurrentResearchIntent(prompt)

        if (heuristicNonSearch && !researchBypass) {
            return ToolIntentActivationDecision(
                activateArtifactTools = activateArtifactTools,
                activateSearchX = false,
                runWebPreRetrieval = false,
            )
        }

        val needsSearch =
            classifier.requiresSearch(
                embedding,
                threshold = SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD,
            )

        return ToolIntentActivationDecision(
            activateArtifactTools = activateArtifactTools,
            activateSearchX = needsSearch && settings.isXSearchSkillConfigured(),
            runWebPreRetrieval = needsSearch,
        )
    }
}
