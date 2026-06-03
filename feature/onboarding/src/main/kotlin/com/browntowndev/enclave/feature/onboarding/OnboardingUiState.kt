package com.browntowndev.enclave.feature.onboarding

import com.browntowndev.enclave.domain.model.onboarding.OnboardingFlowMode
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft

data class OnboardingUiState(
    val flowMode: OnboardingFlowMode = OnboardingFlowMode.FULL,
    val currentStep: Int = 0,
    val ageConfirmed: Boolean = false,
    val automaticErrorTrackingEnabled: Boolean = false,
    val tosReviewed: Boolean = false,
    val tosAccepted: Boolean = false,
    val selectedModelChoice: ModelChoice = ModelChoice.UNDECIDED,
    val selectedProvider: ApiProvider? = null,
    val apiKey: String = "",
    val discoveredModels: List<DiscoveredApiModel> = emptyList(),
    val selectedModelId: String = "",
    val isDiscoveringModels: Boolean = false,
    val discoveryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val ttsProviderDraft: TtsProviderDraft? = null,
    val mediaProviderDraft: MediaProviderDraft? = null,
    val isTtsConfigured: Boolean = false,
    val isMediaConfigured: Boolean = false,
)

enum class ModelChoice {
    UNDECIDED,
    LOCAL,
    API,
    /** Existing account with synced model configuration; skip setup steps. */
    RESTORED,
}
