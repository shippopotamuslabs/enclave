package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.browntowndev.pocketcrew.feature.chat.ResponseHapticPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamingResponseHapticEffectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun thinkingPhase_startsHeartbeatPulseExactlyOnce() {
        val controller = FakeStreamingResponseHapticController()
        val phase = mutableStateOf(ResponseHapticPhase.None)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = phase.value,
                controller = controller,
            )
        }

        composeTestRule.runOnIdle {
            phase.value = ResponseHapticPhase.ThinkingPulse
        }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.startThinkingPulseCount)
        assertEquals(0, controller.startGeneratingBuzzCount)
        assertEquals(0, controller.stopCount)
        assertEquals("thinking-pulse", controller.activePattern)
    }

    @Test
    fun generatingPhase_switchesFromHeartbeatToRapidBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val phase = mutableStateOf(ResponseHapticPhase.ThinkingPulse)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = phase.value,
                controller = controller,
            )
        }

        composeTestRule.runOnIdle {
            phase.value = ResponseHapticPhase.GeneratingBuzz
        }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.startThinkingPulseCount)
        assertEquals(1, controller.startGeneratingBuzzCount)
        assertEquals(0, controller.stopCount)
        assertEquals("generating-buzz", controller.activePattern)
    }

    @Test
    fun nonePhase_stopsActiveBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val phase = mutableStateOf(ResponseHapticPhase.GeneratingBuzz)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = phase.value,
                controller = controller,
            )
        }

        composeTestRule.runOnIdle {
            phase.value = ResponseHapticPhase.None
        }
        composeTestRule.waitForIdle()

        assertEquals(0, controller.startThinkingPulseCount)
        assertEquals(1, controller.startGeneratingBuzzCount)
        assertEquals(1, controller.stopCount)
        assertEquals("none", controller.activePattern)
    }

    @Test
    fun disabledPreference_neverStartsHaptics() {
        val controller = FakeStreamingResponseHapticController()

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = false,
                phase = ResponseHapticPhase.GeneratingBuzz,
                controller = controller,
            )
        }

        assertEquals(0, controller.startThinkingPulseCount)
        assertEquals(0, controller.startGeneratingBuzzCount)
        assertEquals(0, controller.stopCount)
        assertEquals("none", controller.activePattern)
    }

    @Test
    fun leavingComposition_stopsActivePulse() {
        val controller = FakeStreamingResponseHapticController()
        val showEffect = mutableStateOf(true)

        composeTestRule.setContent {
            if (showEffect.value) {
                StreamingResponseHapticEffect(
                    hapticsEnabled = true,
                    phase = ResponseHapticPhase.ThinkingPulse,
                    controller = controller,
                )
            }
        }

        composeTestRule.runOnIdle {
            showEffect.value = false
        }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.stopCount)
        assertEquals("none", controller.activePattern)
    }

    @Test
    fun unrelatedRecomposition_doesNotRestartActiveBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val inputText = mutableStateOf("A")

        composeTestRule.setContent {
            inputText.value.length
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = ResponseHapticPhase.GeneratingBuzz,
                controller = controller,
            )
        }

        composeTestRule.runOnIdle {
            inputText.value = "AB"
        }
        composeTestRule.waitForIdle()

        assertEquals(0, controller.startThinkingPulseCount)
        assertEquals(1, controller.startGeneratingBuzzCount)
        assertEquals(0, controller.stopCount)
        assertEquals("generating-buzz", controller.activePattern)
    }

    private class FakeStreamingResponseHapticController : StreamingResponseHapticController {
        var startThinkingPulseCount = 0
            private set
        var startGeneratingBuzzCount = 0
            private set
        var stopCount = 0
            private set
        var activePattern = "none"
            private set

        override fun startThinkingPulse() {
            if (activePattern != "thinking-pulse") {
                startThinkingPulseCount += 1
                activePattern = "thinking-pulse"
            }
        }

        override fun startGeneratingBuzz() {
            if (activePattern != "generating-buzz") {
                startGeneratingBuzzCount += 1
                activePattern = "generating-buzz"
            }
        }

        override fun stop() {
            if (activePattern != "none") {
                stopCount += 1
                activePattern = "none"
            }
        }
    }
}
