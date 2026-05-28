package com.browntowndev.enclave.domain.usecase.onboarding

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ModelType

interface BulkAssignApiProviderUseCase {
    /**
     * Saves [credentials] + [apiKey] via [SaveApiCredentialsUseCase], creates one
     * [ApiModelConfiguration] for [modelId], then calls [SetDefaultModelUseCase] for
     * every slot in [targetSlots] pointing to the same configuration ID.
     *
     * Returns the persisted [ApiCredentialsId] on success.
     */
    suspend operator fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        modelId: String,
        displayName: String,
        targetSlots: List<ModelType> = TEXT_SLOTS,
    ): Result<ApiCredentialsId>

    companion object {
        /** The six text-model slots that onboarding wires by default (VISION excluded). */
        val TEXT_SLOTS: List<ModelType> = listOf(
            ModelType.MAIN,
            ModelType.FAST,
            ModelType.THINKING,
            ModelType.DRAFT_ONE,
            ModelType.DRAFT_TWO,
            ModelType.FINAL_SYNTHESIS,
        )
    }
}
