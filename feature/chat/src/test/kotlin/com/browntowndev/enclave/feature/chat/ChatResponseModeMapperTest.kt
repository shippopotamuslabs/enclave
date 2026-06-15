package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ChatResponseModeMapperTest {
    @Nested
    @DisplayName("E01 — OTHER without override is not sendable")
    inner class OtherWithoutOverride {
        @Test
        @DisplayName("E01 — OTHER with null override throws IllegalArgumentException with exact message")
        fun e01_otherWithoutOverrideThrows() {
            val exception =
                assertThrows(IllegalArgumentException::class.java) {
                    ChatResponseModeMapper.fromSelection(
                        selectedMode = ChatModeUi.OTHER,
                        override = null,
                    )
                }

            assertEquals(
                "Cannot send in OTHER mode without a selected preset",
                exception.message,
            )
        }
    }

    @Nested
    @DisplayName("S06 — Slot modes produce slot response modes (no override field)")
    inner class SlotModeMapping {
        @Test
        @DisplayName("S06 — FAST maps to ChatResponseMode.Fast")
        fun s06_fastMapsToFast() {
            val result =
                ChatResponseModeMapper.fromSelection(
                    selectedMode = ChatModeUi.FAST,
                    override = null,
                )

            assertEquals(ChatResponseMode.Fast, result)
        }

        @Test
        @DisplayName("S06 — THINKING maps to ChatResponseMode.Thinking")
        fun s06_thinkingMapsToThinking() {
            val result =
                ChatResponseModeMapper.fromSelection(
                    selectedMode = ChatModeUi.THINKING,
                    override = null,
                )

            assertEquals(ChatResponseMode.Thinking, result)
        }

        @Test
        @DisplayName("S06 — COUNCIL maps to ChatResponseMode.Council")
        fun s06_councilMapsToCouncil() {
            val result =
                ChatResponseModeMapper.fromSelection(
                    selectedMode = ChatModeUi.COUNCIL,
                    override = null,
                )

            assertEquals(ChatResponseMode.Council, result)
        }
    }
}
