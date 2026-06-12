package com.browntowndev.enclave.feature.chat.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class StreamingResponseHapticEffectLifecycleTest {
    @get:Rule
    val androidComposeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appBackgrounded_stopsActiveThinkingPulse() {
        val controller = FakeStreamingResponseHapticController()

        androidComposeRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = ResponseHapticPhase.ThinkingPulse,
                controller = controller,
            )
        }
        androidComposeRule.waitForIdle()

        assertEquals(1, controller.startThinkingPulseCount)

        androidComposeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        androidComposeRule.waitForIdle()

        assertEquals(1, controller.stopCount)
        assertEquals("none", controller.activePattern)
    }

    @Test
    fun appForegrounded_afterBackground_resumesThinkingPulseWhenStillThinking() {
        val controller = FakeStreamingResponseHapticController()

        androidComposeRule.setContent {
            StreamingResponseHapticEffect(
                hapticsEnabled = true,
                phase = ResponseHapticPhase.ThinkingPulse,
                controller = controller,
            )
        }
        androidComposeRule.waitForIdle()
        androidComposeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        androidComposeRule.waitForIdle()

        androidComposeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        androidComposeRule.waitForIdle()

        assertEquals(2, controller.startThinkingPulseCount)
        assertEquals("thinking-pulse", controller.activePattern)
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
