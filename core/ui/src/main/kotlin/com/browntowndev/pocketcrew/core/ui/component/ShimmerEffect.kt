package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A modifier that applies a shimmering effect to a composable's background.
 * Optimized to perform calculations and state reads only during the drawing phase,
 * avoiding unnecessary recompositions and layout passes.
 */
fun Modifier.shimmerEffect(
    baseColor: Color = Color.LightGray.copy(alpha = 0.3f),
    highlightColor: Color = Color.LightGray.copy(alpha = 0.5f),
    durationMillis: Int = 1500
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progressState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    this.shimmerEffect(
        progressState = progressState,
        baseColor = baseColor,
        highlightColor = highlightColor
    )
}

/**
 * A stateless version of [shimmerEffect] that accepts a hoisted [progressState].
 * Reading the state inside [drawBehind] ensures that only the draw phase is re-run
 * when the animation updates, preventing full recompositions of the UI tree.
 */
fun Modifier.shimmerEffect(
    progressState: State<Float>,
    baseColor: Color,
    highlightColor: Color
): Modifier = this.drawBehind {
    val width = size.width
    val height = size.height
    val progress = progressState.value
    
    // Map 0f..1f to a sweep range (-2x to 2x width) to ensure the gradient fully clears the view
    val startOffsetX = (-2 * width) + (progress * (4 * width))

    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                baseColor,
                highlightColor,
                baseColor,
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + width, height)
        )
    )
}
