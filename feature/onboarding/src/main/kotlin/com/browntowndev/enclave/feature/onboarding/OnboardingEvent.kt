package com.browntowndev.enclave.feature.onboarding

sealed interface OnboardingEvent {
    data object NavigateToChat : OnboardingEvent
}
