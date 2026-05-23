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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Snake order through the 3×3 matrix (row-major). */
private val MATRIX_SNAKE_SEQUENCE = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)

private const val MATRIX_GRID_SIZE_DP = 22
private const val MATRIX_CELL_GAP_FRACTION = 0.14f
private const val MATRIX_STEP_MS = 220L
private const val MATRIX_CROSSFADE_MS = 140

/**
 * Neo-brutalist 3×3 cell matrix: hard-edged tiles step through a fixed sequence with a
 * short crossfade (no pulsing circles or gradient orb).
 *
 * Outer [modifier] keeps the 46 dp layout slot used beside "Thinking…" / "Responding…" rows.
 */
@Composable
fun DynamicThinkingAnimation(modifier: Modifier = Modifier) {
    var sequenceIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MATRIX_STEP_MS.milliseconds)
            sequenceIndex = (sequenceIndex + 1) % MATRIX_SNAKE_SEQUENCE.size
        }
    }

    val activeCell = MATRIX_SNAKE_SEQUENCE[sequenceIndex]

    val c0 by cellAlpha(activeCell, 0)
    val c1 by cellAlpha(activeCell, 1)
    val c2 by cellAlpha(activeCell, 2)
    val c3 by cellAlpha(activeCell, 3)
    val c4 by cellAlpha(activeCell, 4)
    val c5 by cellAlpha(activeCell, 5)
    val c6 by cellAlpha(activeCell, 6)
    val c7 by cellAlpha(activeCell, 7)
    val c8 by cellAlpha(activeCell, 8)

    val activeFill = MaterialTheme.colorScheme.primary
    val idleFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Canvas(modifier = Modifier.size(MATRIX_GRID_SIZE_DP.dp)) {
            val cellAlphas = floatArrayOf(c0, c1, c2, c3, c4, c5, c6, c7, c8)
            val gap = size.minDimension * MATRIX_CELL_GAP_FRACTION
            val cellSize = (size.minDimension - gap * 2f) / 3f
            val cornerPx = EnclaveNeoTokens.cornerRadius.toPx().coerceAtMost(cellSize / 4f)
            val strokePx = (EnclaveNeoTokens.borderWidth / 2).toPx()

            // Outer frame — matches neo panels
            drawRoundRect(
                color = borderColor,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(width = strokePx),
            )

            for (index in 0 until 9) {
                val col = index % 3
                val row = index / 3
                val topLeft =
                    Offset(
                        x = gap + col * cellSize,
                        y = gap + row * cellSize,
                    )
                val alpha = cellAlphas[index]
                val fill =
                    androidx.compose.ui.graphics.lerp(
                        idleFill,
                        activeFill,
                        alpha,
                    )
                drawRoundRect(
                    color = fill,
                    topLeft = topLeft,
                    size = Size(cellSize, cellSize),
                    cornerRadius = CornerRadius(cornerPx),
                )
            }
        }
    }
}

@Composable
private fun cellAlpha(
    activeCell: Int,
    cellIndex: Int,
): androidx.compose.runtime.State<Float> =
    animateFloatAsState(
        targetValue = if (activeCell == cellIndex) 1f else 0f,
        animationSpec = tween(durationMillis = MATRIX_CROSSFADE_MS),
        label = "neoMatrixCell$cellIndex",
    )

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, backgroundColor = 0xFF18181B, name = "Neo thinking matrix")
@Composable
private fun PreviewDynamicThinkingAnimation() {
    EnclaveTheme(darkTheme = true) {
        DynamicThinkingAnimation(modifier = Modifier.size(46.dp))
    }
}
