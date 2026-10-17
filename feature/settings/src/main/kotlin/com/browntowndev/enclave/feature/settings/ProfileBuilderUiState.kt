package com.browntowndev.enclave.feature.settings

import java.util.UUID

data class ProfileBuilderUiState(
    val currentStep: Int = 0,
    // Step 0: model selection
    val configuredModels: List<ConfiguredModelUi> = emptyList(),
    val selectedConfigId: String? = null,
    // Step 1: file import
    val selectedFileUris: List<String> = emptyList(),
    val selectedFileNames: List<String> = emptyList(),
    // Step 2: cost estimate
    val isPreparingCorpus: Boolean = false,
    val costEstimate: ProfileCostEstimateUi? = null,
    val corpusRunId: String = UUID.randomUUID().toString(),
    // Step 3: analysis progress
    val analysisWorkId: String? = null,
    val currentPass: Int = 0,
    val totalPasses: Int = 4,
    val isAnalysisRunning: Boolean = false,
    val isAnalysisComplete: Boolean = false,
    val analysisError: String? = null,
    // Step 4: review
    val profileContent: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    // General
    val errorMessage: String? = null,
) {
    val selectedModel: ConfiguredModelUi?
        get() = configuredModels.firstOrNull { it.id == selectedConfigId }

    val canProceedFromStep0: Boolean
        get() = selectedConfigId != null

    val canProceedFromStep1: Boolean
        get() = selectedFileUris.isNotEmpty()
}

const val PROFILE_BUILDER_STEP_MODEL = 0
const val PROFILE_BUILDER_STEP_FILES = 1
const val PROFILE_BUILDER_STEP_COST = 2
const val PROFILE_BUILDER_STEP_PROGRESS = 3
const val PROFILE_BUILDER_STEP_REVIEW = 4
const val PROFILE_BUILDER_TOTAL_STEPS = 5
