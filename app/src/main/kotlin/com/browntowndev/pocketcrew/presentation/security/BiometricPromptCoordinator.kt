package com.browntowndev.pocketcrew.presentation.security

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
}
