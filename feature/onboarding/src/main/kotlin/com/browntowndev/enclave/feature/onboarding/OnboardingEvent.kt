package com.browntowndev.enclave.feature.onboarding

import com.browntowndev.enclave.domain.model.inference.ModelType

sealed interface OnboardingEvent {
    data class NavigateToChat(
        val modelChoice: ModelChoice,
        val apiSlots: Set<ModelType>,
    ) : OnboardingEvent
}
