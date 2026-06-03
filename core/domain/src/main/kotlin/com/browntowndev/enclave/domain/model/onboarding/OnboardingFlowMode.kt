package com.browntowndev.enclave.domain.model.onboarding

/**
 * Determines which onboarding steps are shown.
 *
 * [RESTORED] applies when the user signed into an existing account and synced
 * configuration (API providers and/or default model slots) already satisfies setup.
 */
enum class OnboardingFlowMode {
    FULL,
    RESTORED,
}
