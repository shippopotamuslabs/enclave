package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

/**
 * Neo-brutalist circular progress indicator. Uses flat [StrokeCap.Butt] ends and a
 * visible ring track keyed to the neo border colour instead of M3's rounded, trackless default.
 *
 * Pass [progress] for a determinate indicator (0f–1f); omit it for indeterminate.
 */
@Composable
fun NeoCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 3.dp,
    progress: (() -> Float)? = null,
) {
    if (progress == null) {
        val transition = rememberInfiniteTransition(label = "neo_spinner")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = LinearEasing),
                ),
            label = "rotation",
        )
        Canvas(modifier = modifier.size(32.dp)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val style = Stroke(width = strokePx, cap = StrokeCap.Butt)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
            drawArc(
                color = color,
                startAngle = rotation - 90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
        }
    } else {
        Canvas(modifier = modifier.size(32.dp)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val style = Stroke(width = strokePx, cap = StrokeCap.Butt)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = progress() * 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF18181B)
@Composable
@Suppress("UnusedPrivateMember")
private fun NeoCircularProgressIndicatorPreview() {
    EnclaveTheme {
        NeoCircularProgressIndicator()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF18181B)
@Composable
@Suppress("UnusedPrivateMember")
private fun NeoCircularProgressIndicatorDeterminatePreview() {
    EnclaveTheme {
        NeoCircularProgressIndicator(progress = { 0.6f })
    }
}
