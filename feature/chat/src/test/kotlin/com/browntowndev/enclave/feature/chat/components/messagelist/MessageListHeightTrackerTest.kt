package com.browntowndev.enclave.feature.chat.components.messagelist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MessageListHeightTracker")
class MessageListHeightTrackerTest {
    private val heightKey = "chat-1:msg-1"

    @Test
    @DisplayName("SC-PERF-13 — height tracker ignores sub-8px oscillations")
    fun scPerf13_ignoresSubThresholdOscillations() {
        val tracker = MessageListHeightTracker()
        var commitCount = 0

        repeat(50) { iteration ->
            val heightPx = if (iteration % 2 == 0) 200 else 201
            if (tracker.onSizeChanged(heightKey, heightPx)) {
                commitCount++
            }
        }

        assertEquals(1, commitCount)
        assertEquals(200, tracker.get(heightKey))
    }

    @Test
    @DisplayName("SC-PERF-13 — height tracker commits when delta meets threshold")
    fun scPerf13_commitsWhenDeltaMeetsThreshold() {
        val tracker = MessageListHeightTracker()
        assertTrue(tracker.onSizeChanged(heightKey, 200))

        val committed = tracker.onSizeChanged(heightKey, 208)

        assertTrue(committed)
        assertEquals(208, tracker.get(heightKey))
    }
}
