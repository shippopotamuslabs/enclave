package com.browntowndev.pocketcrew.feature.chat.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.browntowndev.pocketcrew.feature.chat.ResponseHapticPhase

private val THINKING_PULSE_TIMINGS_MS = longArrayOf(0L, 45L, 130L, 55L, 920L)
private val THINKING_PULSE_AMPLITUDES = intArrayOf(0, 55, 0, 75, 0)
private val GENERATING_BUZZ_TIMINGS_MS = longArrayOf(0L, 12L, 48L, 14L, 70L)
private val GENERATING_BUZZ_AMPLITUDES = intArrayOf(0, 70, 0, 85, 0)

interface StreamingResponseHapticController {
    fun startThinkingPulse()

    fun startGeneratingBuzz()

    fun stop()
}

@Composable
fun StreamingResponseHapticEffect(
    hapticsEnabled: Boolean,
    phase: ResponseHapticPhase,
    pulseKey: Long = 0L,
    controller: StreamingResponseHapticController = rememberStreamingResponseHapticController(),
) {
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    LaunchedEffect(hapticsEnabled, phase, pulseKey, controller) {
        when {
            !hapticsEnabled -> controller.stop()
            phase == ResponseHapticPhase.ThinkingPulse -> {
                controller.stop()
                controller.startThinkingPulse()
            }
            phase == ResponseHapticPhase.GeneratingBuzz && pulseKey > 0L -> {
                controller.stop()
                controller.startGeneratingBuzz()
            }
            else -> controller.stop()
        }
    }
}

@Composable
private fun rememberStreamingResponseHapticController(): StreamingResponseHapticController {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidStreamingResponseHapticController(context)
    }
}

private class AndroidStreamingResponseHapticController(
    context: Context,
) : StreamingResponseHapticController {
    private val vibrator: Vibrator =
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    private var activePattern: ResponseHapticPhase = ResponseHapticPhase.None

    override fun startThinkingPulse() {
        startPattern(
            phase = ResponseHapticPhase.ThinkingPulse,
            timings = THINKING_PULSE_TIMINGS_MS,
            amplitudes = THINKING_PULSE_AMPLITUDES,
        )
    }

    override fun startGeneratingBuzz() {
        startPattern(
            phase = ResponseHapticPhase.GeneratingBuzz,
            timings = GENERATING_BUZZ_TIMINGS_MS,
            amplitudes = GENERATING_BUZZ_AMPLITUDES,
            repeatIndex = NO_REPEAT,
        )
    }

    override fun stop() {
        if (activePattern == ResponseHapticPhase.None) return
        vibrator.cancel()
        activePattern = ResponseHapticPhase.None
    }

    private fun startPattern(
        phase: ResponseHapticPhase,
        timings: LongArray,
        amplitudes: IntArray,
        repeatIndex: Int = REPEAT_FROM_START,
    ) {
        if (activePattern == phase) return
        activePattern = phase
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, repeatIndex))
        if (repeatIndex == NO_REPEAT) {
            activePattern = ResponseHapticPhase.None
        }
    }

    private companion object {
        private const val REPEAT_FROM_START = 0
        private const val NO_REPEAT = -1
    }
}
