package com.browntowndev.enclave.domain.model.chat

/**
 * Domain enum representing chat modes.
 * Used by GenerateChatResponseUseCase to determine inference behavior.
 */
enum class Mode {
    /** Fast mode - uses fast model for quick responses */
    FAST,

    /** Thinking mode - uses reasoning model for complex tasks */
    THINKING,

    /** Council mode - uses multi-model pipeline */
    COUNCIL;

    companion object {
        val DEFAULT = Mode.FAST
    }
}
