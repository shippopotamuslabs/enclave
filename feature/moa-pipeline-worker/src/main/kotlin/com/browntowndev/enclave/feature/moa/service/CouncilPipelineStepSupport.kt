package com.browntowndev.enclave.feature.moa.service

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort

internal object CouncilPipelineStepSupport {
    fun getModelTypeForStep(step: PipelineStep): ModelType =
        when (step) {
            PipelineStep.DRAFT_ONE -> ModelType.DRAFT_ONE
            PipelineStep.DRAFT_TWO -> ModelType.DRAFT_TWO
            PipelineStep.SYNTHESIS -> ModelType.MAIN
            PipelineStep.FINAL -> ModelType.FINAL_SYNTHESIS
        }

    suspend fun getModelDisplayNameForStep(
        step: PipelineStep,
        activeModelProvider: ActiveModelProviderPort,
    ): String {
        val modelType = getModelTypeForStep(step)
        return activeModelProvider.getActiveConfiguration(modelType)?.name ?: modelType.name
    }
}
