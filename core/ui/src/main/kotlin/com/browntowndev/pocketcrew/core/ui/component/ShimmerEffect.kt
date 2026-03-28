package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * A stateless modifier that applies a shimmering effect to a composable's background.
 * Optimized to avoid object allocations in the draw loop by using [drawWithCache]
 * and animating via canvas translation instead of shader recreation.
 *
 * @param progressState An animated 0f..1f value representing the shimmer progress.
 * @param baseColor The base color of the skeleton item.
 * @param highlightColor The color of the shimmering sweep.
 */
fun Modifier.shimmerEffect(
    progressState: State<Float>,
    baseColor: Color,
    highlightColor: Color
): Modifier = this.drawWithCache {
    // 1. Create the brush once per size change.
    // The brush remains static relative to its own coordinate system.
    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor,
        ),
        // We define the brush to match the component size
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(size.width, size.height)
    )

    onDrawBehind {
        val width = size.width
        val progress = progressState.value
        
        // 2. Animate by translating the canvas.
        // We sweep from -width (gradient ends at 0) to width (gradient starts at width).
        val xOffset = -width + (progress * 2 * width)

        withTransform({
            translate(left = xOffset)
        }) {
            // 3. Draw the cached brush. 
            // The canvas translation handles the movement.
            drawRect(brush = brush, size = size)
        }
    }
}
