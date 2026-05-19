package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
        val animatedOffset =
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "shimmerOffset",
            )

        val bandStrength = (1f - highlightColor.alpha).coerceIn(0f, 1f)
        Text(
            text = text,
            style = style.copy(color = baseColor),
            modifier =
                modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val offset = animatedOffset.value
                            val brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = bandStrength),
                                            Color.Transparent,
                                        ),
                                    start = Offset(offset - 200f, 0f),
                                    end = Offset(offset, 0f),
                                )
                            drawRect(brush = brush, blendMode = BlendMode.DstOut)
                        }
                    },
        )
    } else {
        Text(
            text = text,
            style = style.copy(color = baseColor),
            modifier = modifier,
        )
    }
}
