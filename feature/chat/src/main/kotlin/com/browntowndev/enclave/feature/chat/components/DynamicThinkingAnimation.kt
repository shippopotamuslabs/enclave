package com.browntowndev.enclave.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// Pairs of dot indices (0–8 in a 3×3 grid) that light up together.
// No consecutive pair shares a dot, so every step produces a clean crossfade.
private val ACTIVATION_SEQUENCE =
    listOf(
        0 to 8,
        2 to 6,
        1 to 7,
        3 to 5,
        4 to 0,
        8 to 3,
        2 to 7,
        5 to 1,
        6 to 4,
        0 to 5,
        8 to 1,
        3 to 6,
        4 to 2,
        7 to 0,
        5 to 8,
        1 to 4,
    )

/**
 * 3×3 dot-grid animation: two dots illuminate at a time using the primary colour,
 * cycling through a fixed sequence every 500 ms with a 400 ms alpha crossfade.
 * Replaces the old per-pixel disco-ball which ran complex trig math at 60 fps.
 *
 * Outer Box matches the 46 dp touch/layout footprint of the old orb; the Canvas
 * is sized to the same 24 dp the old Spacer used, keeping visual alignment identical.
 */
@Composable
fun DynamicThinkingAnimation(modifier: Modifier = Modifier) {
    var stepIndex by remember { mutableIntStateOf((0 until ACTIVATION_SEQUENCE.size).random()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L.milliseconds)
            stepIndex = (stepIndex + 1) % ACTIVATION_SEQUENCE.size
        }
    }

    val (activeA, activeB) = ACTIVATION_SEQUENCE[stepIndex]

    // One animateFloatAsState per dot — drives both opacity and radius from a single value.
    // Called unconditionally at the top level per Compose rules.
    val a0 by animateFloatAsState(targetValue = if (activeA == 0 || activeB == 0) 1f else 0.15f, animationSpec = tween(400), label = "a0")
    val a1 by animateFloatAsState(targetValue = if (activeA == 1 || activeB == 1) 1f else 0.15f, animationSpec = tween(400), label = "a1")
    val a2 by animateFloatAsState(targetValue = if (activeA == 2 || activeB == 2) 1f else 0.15f, animationSpec = tween(400), label = "a2")
    val a3 by animateFloatAsState(targetValue = if (activeA == 3 || activeB == 3) 1f else 0.15f, animationSpec = tween(400), label = "a3")
    val a4 by animateFloatAsState(targetValue = if (activeA == 4 || activeB == 4) 1f else 0.15f, animationSpec = tween(400), label = "a4")
    val a5 by animateFloatAsState(targetValue = if (activeA == 5 || activeB == 5) 1f else 0.15f, animationSpec = tween(400), label = "a5")
    val a6 by animateFloatAsState(targetValue = if (activeA == 6 || activeB == 6) 1f else 0.15f, animationSpec = tween(400), label = "a6")
    val a7 by animateFloatAsState(targetValue = if (activeA == 7 || activeB == 7) 1f else 0.15f, animationSpec = tween(400), label = "a7")
    val a8 by animateFloatAsState(targetValue = if (activeA == 8 || activeB == 8) 1f else 0.15f, animationSpec = tween(400), label = "a8")

    val dotColor = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val dotAlphas = floatArrayOf(a0, a1, a2, a3, a4, a5, a6, a7, a8)
            val baseRadius = size.minDimension / 11f
            val pad = size.width * 0.08f
            val spacingX = (size.width - 2f * pad) / 2f
            val spacingY = (size.height - 2f * pad) / 2f

            for (i in 0 until 9) {
                val alpha = dotAlphas[i]
                val center =
                    Offset(
                        x = pad + (i % 3) * spacingX,
                        y = pad + (i / 3) * spacingY,
                    )
                drawCircle(
                    color = dotColor.copy(alpha = alpha),
                    radius = baseRadius * (0.55f + alpha * 0.7f),
                    center = center,
                )
            }
        }
    }
}
