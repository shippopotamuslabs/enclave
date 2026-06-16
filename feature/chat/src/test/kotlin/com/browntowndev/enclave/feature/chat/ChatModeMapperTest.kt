package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatModeMapperTest {
    @Test
    @DisplayName("S16 — ChatModeUi.OTHER maps to Mode.OTHER")
    fun s16_otherToDomain() {
        val result = ChatModeMapper.run { ChatModeUi.OTHER.toDomain() }

        assertEquals(Mode.OTHER, result)
    }

    @Test
    @DisplayName("S16 — Mode.OTHER maps to ChatModeUi.OTHER")
    fun s16_otherToUi() {
        val result = ChatModeMapper.run { Mode.OTHER.toUi() }

        assertEquals(ChatModeUi.OTHER, result)
    }
}
