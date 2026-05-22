package com.browntowndev.enclave.presentation.security

internal class BiometricPromptCoordinator {
    private var isPromptInFlight = false

    fun tryStartPrompt(
        isEnabled: Boolean,
        isUnlocking: Boolean,
        onPromptNeeded: () -> Unit,
    ) {
        if (!isEnabled || isUnlocking || isPromptInFlight) return

        isPromptInFlight = true
        onPromptNeeded()
    }

    fun finishPrompt() {
        isPromptInFlight = false
    }

    /**
     * Starts a user-initiated biometric prompt. Clears any stale in-flight lock left
     * by an auto-triggered attempt (e.g. dismissed sheet) so manual retries work.
     */
    fun tryStartUserPrompt(
        isEnabled: Boolean,
        onPromptNeeded: () -> Unit,
    ) {
        if (!isEnabled) return

        isPromptInFlight = false
        tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
            onPromptNeeded = onPromptNeeded,
        )
    }
}
