package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun ShimmerText(
    text: String,
    baseColor: Color,
    highlightColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (enabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerOffset",
        )

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(shimmerOffset - 200f, 0f),
            end = Offset(shimmerOffset, 0f),
        )

        Text(
            text = text,
            style = style.copy(brush = shimmerBrush),
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            style = style.copy(color = baseColor),
            modifier = modifier,
        )
    }
}
