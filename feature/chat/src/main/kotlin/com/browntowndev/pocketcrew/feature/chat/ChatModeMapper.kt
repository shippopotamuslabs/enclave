package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.Mode

/**
 * Maps between presentation [ChatModeUi] and domain [Mode].
 */
object ChatModeMapper {

    fun ChatModeUi.toDomain(): Mode = when (this) {
        ChatModeUi.FAST -> Mode.FAST
        ChatModeUi.THINKING -> Mode.THINKING
        ChatModeUi.CREW -> Mode.CREW
    }

    fun Mode.toUi(): ChatModeUi = when (this) {
        Mode.FAST -> ChatModeUi.FAST
        Mode.THINKING -> ChatModeUi.THINKING
        Mode.CREW -> ChatModeUi.CREW
    }
}
