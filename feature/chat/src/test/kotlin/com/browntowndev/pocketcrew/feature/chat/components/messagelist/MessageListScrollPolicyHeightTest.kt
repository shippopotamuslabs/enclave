package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Message list scroll policy — height commit threshold")
class MessageListScrollPolicyHeightTest {
    @Test
    @DisplayName("SC-PERF-11 — shouldCommitMessageHeightPx rejects sub-threshold delta")
    fun scPerf11_rejectsSubThresholdDelta() {
        val result =
            shouldCommitMessageHeightPx(
                previousHeightPx = 100,
                newHeightPx = 103,
                minDeltaPx = 8,
            )

        assertFalse(result)
    }

    @Test
    @DisplayName("SC-PERF-12 — shouldCommitMessageHeightPx accepts first measurement")
    fun scPerf12_acceptsFirstMeasurement() {
        val result =
            shouldCommitMessageHeightPx(
                previousHeightPx = null,
                newHeightPx = 50,
                minDeltaPx = 8,
            )

        assertTrue(result)
    }

    @Test
    @DisplayName("SC-PERF-14 — MessageListLazyCacheWindow equals 360.dp")
    fun scPerf14_lazyCacheWindowIs360Dp() {
        assertEquals(360.dp, MessageListLazyCacheWindowDp)
    }
}
