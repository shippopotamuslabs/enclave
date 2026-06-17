package com.browntowndev.enclave.feature.chat.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import com.browntowndev.enclave.feature.chat.ResponseHapticPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun generatingPhase_withPulseKey_startsRapidBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val pulseKey = mutableStateOf(0L)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = ResponseHapticPhase.GeneratingBuzz,
                pulseKey = pulseKey.value,
                controller = controller,
            )
        }

        composeTestRule.runOnIdle {
            pulseKey.value = 1L
        }
        composeTestRule.waitForIdle()

        assertEquals(0, controller.startThinkingPulseCount)
        assertEquals(1, controller.startGeneratingBuzzCount)
        assertEquals(0, controller.stopCount)
        assertEquals("generating-buzz", controller.activePattern)
    }

    @Test
    fun generatingPhase_afterThinkingPulse_stopsHeartbeatBeforeBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val phase = mutableStateOf(ResponseHapticPhase.ThinkingPulse)
        val pulseKey = mutableStateOf(0L)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = phase.value,
                pulseKey = pulseKey.value,
                controller = controller,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            phase.value = ResponseHapticPhase.GeneratingBuzz
            pulseKey.value = 1L
        }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.startThinkingPulseCount)
        assertEquals(1, controller.stopCount)
        assertEquals(1, controller.startGeneratingBuzzCount)
        assertEquals("generating-buzz", controller.activePattern)
    }

    @Test
    fun nonePhase_afterThinkingPulse_stopsHeartbeat() {
        val controller = FakeStreamingResponseHapticController()
        val phase = mutableStateOf(ResponseHapticPhase.ThinkingPulse)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = phase.value,
                controller = controller,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            phase.value = ResponseHapticPhase.None
        }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.startThinkingPulseCount)
        assertEquals(1, controller.stopCount)
        assertEquals("none", controller.activePattern)
    }

    @Test
    fun nonePhase_stopsActiveBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val phase = mutableStateOf(ResponseHapticPhase.GeneratingBuzz)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = phase.value,
                pulseKey = 1L,
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
                pulseKey = 1L,
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
                    phase = ResponseHapticPhase.GeneratingBuzz,
                    pulseKey = 1L,
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
                pulseKey = 1L,
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

    @Test
    fun pulseKeyChange_restartsActiveBuzz() {
        val controller = FakeStreamingResponseHapticController()
        val pulseKey = mutableStateOf(1L)

        composeTestRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = ResponseHapticPhase.GeneratingBuzz,
                pulseKey = pulseKey.value,
                controller = controller,
            )
        }

        composeTestRule.runOnIdle {
            pulseKey.value = 2L
        }
        composeTestRule.waitForIdle()

        assertEquals(0, controller.startThinkingPulseCount)
        assertEquals(2, controller.startGeneratingBuzzCount)
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
            startGeneratingBuzzCount += 1
            activePattern = "generating-buzz"
        }

        override fun stop() {
            if (activePattern != "none") {
                stopCount += 1
                activePattern = "none"
            }
        }
    }
}
