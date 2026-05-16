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

private val THINKING_PULSE_TIMINGS_MS = longArrayOf(0L, 70L, 90L, 90L, 740L)
private val THINKING_PULSE_AMPLITUDES = intArrayOf(0, 135, 0, 190, 0)
private val GENERATING_BUZZ_TIMINGS_MS = longArrayOf(0L, 24L, 24L, 18L, 32L, 28L, 20L, 18L, 42L)
private val GENERATING_BUZZ_AMPLITUDES = intArrayOf(0, 170, 0, 225, 0, 150, 0, 210, 0)

interface StreamingResponseHapticController {
    fun startThinkingPulse()
    fun startGeneratingBuzz()
    fun stop()
}

@Composable
fun StreamingResponseHapticEffect(
    hapticsEnabled: Boolean,
    phase: ResponseHapticPhase,
    controller: StreamingResponseHapticController = rememberStreamingResponseHapticController(),
) {
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    LaunchedEffect(hapticsEnabled, phase, controller) {
        when {
            !hapticsEnabled -> controller.stop()
            phase == ResponseHapticPhase.ThinkingPulse -> controller.startThinkingPulse()
            phase == ResponseHapticPhase.GeneratingBuzz -> controller.startGeneratingBuzz()
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
    ) {
        if (activePattern == phase) return
        activePattern = phase
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
    }
}
