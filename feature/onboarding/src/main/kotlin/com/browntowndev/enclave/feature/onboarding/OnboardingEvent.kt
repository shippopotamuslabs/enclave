package com.browntowndev.enclave.feature.onboarding

sealed interface OnboardingEvent {
    data class NavigateToChat(
        val modelChoice: ModelChoice,
    ) : OnboardingEvent
}
