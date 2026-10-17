package com.browntowndev.enclave.feature.onboarding

data class OnboardingUiState(
    val flowMode: OnboardingFlowModeUi = OnboardingFlowModeUi.FULL,
    val currentStep: Int = 0,
    val ageConfirmed: Boolean = false,
    val automaticErrorTrackingEnabled: Boolean = false,
    val tosReviewed: Boolean = false,
    val tosAccepted: Boolean = false,
    val selectedModelChoice: ModelChoice = ModelChoice.UNDECIDED,
    val selectedProvider: ApiProviderUi? = null,
    val apiKey: String = "",
    val baseUrl: String = "",
    val discoveredModels: List<DiscoveredApiModelUi> = emptyList(),
    val selectedModelId: String = "",
    val isDiscoveringModels: Boolean = false,
    val discoveryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val ttsProviderDraft: TtsProviderDraftUi? = null,
    val mediaProviderDraft: MediaProviderDraftUi? = null,
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
