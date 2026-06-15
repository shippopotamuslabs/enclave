package com.browntowndev.enclave.domain.model.chat

/**
 * Discriminated union for chat inference dispatch.
 *
 * Invalid states (e.g. FAST + override) are unrepresentable — callers cannot
 * pass a slot mode and an override in the same argument list.
 */
sealed interface ChatResponseMode {
    data object Fast : ChatResponseMode

    data object Thinking : ChatResponseMode

    data object Council : ChatResponseMode

    data class Other(val modelOverride: ChatModelOverride) : ChatResponseMode
}
