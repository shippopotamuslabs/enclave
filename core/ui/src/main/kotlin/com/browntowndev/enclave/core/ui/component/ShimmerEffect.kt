package com.browntowndev.enclave.core.ui.component

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.hypot

/**
 * A stateless modifier that applies a shimmering effect to a composable's background.
 * Optimized to avoid object allocations in the draw loop by using [drawWithCache]
 * and animating via canvas translation instead of shader recreation.
 *
 * The gradient repeats every diagonal period so [progressState] 0f and 1f look identical
 * (no visible jump on [androidx.compose.animation.core.RepeatMode.Restart]).
 *
 * @param progressState An animated 0f..1f value representing the shimmer progress.
 * @param baseColor The base color of the skeleton item.
 * @param highlightColor The color of the shimmering sweep.
 */
fun Modifier.shimmerEffect(
    progressState: State<Float>,
    baseColor: Color,
    highlightColor: Color,
): Modifier =
    this.drawWithCache {
        val width = size.width
        val height = size.height
        val period = hypot(width.toDouble(), height.toDouble()).toFloat().coerceAtLeast(1f)
        val unitX = width / period
        val unitY = height / period

        // Diagonal cross-shimmer: two base→highlight→base bands over 2× period.
        val brush =
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0.0f to baseColor,
                        0.25f to highlightColor,
                        0.5f to baseColor,
                        0.75f to highlightColor,
                        1.0f to baseColor,
                    ),
                start = Offset.Zero,
                end = Offset(width * 2f, height * 2f),
            )

        onDrawBehind {
            val progress = progressState.value.coerceIn(0f, 1f)
            val shift = progress * period

            withTransform({
                translate(left = -shift * unitX, top = -shift * unitY)
            }) {
                drawRect(
                    brush = brush,
                    topLeft = Offset(shift * unitX, shift * unitY),
                    size = size,
                )
            }
        }
    }
