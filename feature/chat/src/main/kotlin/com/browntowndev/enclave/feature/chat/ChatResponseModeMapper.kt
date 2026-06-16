package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode

/**
 * Builds the domain dispatch type [ChatResponseMode] from ephemeral UI state
 * at send time. Invalid combinations (OTHER without override) are blocked here.
 */
object ChatResponseModeMapper {
    fun fromSelection(
        selectedMode: ChatModeUi,
        override: ChatModelOverride?,
    ): ChatResponseMode =
        when (selectedMode) {
            ChatModeUi.FAST -> ChatResponseMode.Fast
            ChatModeUi.THINKING -> ChatResponseMode.Thinking
            ChatModeUi.COUNCIL -> ChatResponseMode.Council
            ChatModeUi.OTHER ->
                ChatResponseMode.Other(
                    modelOverride =
                        requireNotNull(override) {
                            "Cannot send in OTHER mode without a selected preset"
                        },
                )
        }
}
