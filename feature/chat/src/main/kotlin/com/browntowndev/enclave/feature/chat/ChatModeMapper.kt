package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.Mode

/**
 * Maps between presentation [ChatModeUi] and domain [Mode].
 */
object ChatModeMapper {
    fun ChatModeUi.toDomain(): Mode =
        when (this) {
            ChatModeUi.FAST -> Mode.FAST
            ChatModeUi.THINKING -> Mode.THINKING
            ChatModeUi.COUNCIL -> Mode.COUNCIL
        }

    fun Mode.toUi(): ChatModeUi =
        when (this) {
            Mode.FAST -> ChatModeUi.FAST
            Mode.THINKING -> ChatModeUi.THINKING
            Mode.COUNCIL -> ChatModeUi.COUNCIL
        }
}
