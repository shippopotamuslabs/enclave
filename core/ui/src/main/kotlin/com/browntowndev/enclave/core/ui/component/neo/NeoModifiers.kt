package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.browntowndev.enclave.core.ui.theme.DarkNeoShadow
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.LightNeoShadow

@Composable
fun neoBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
fun neoShadowColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DarkNeoShadow
    } else {
        LightNeoShadow
    }

@Composable
fun neoButtonShadowOffset(): Dp =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        EnclaveNeoTokens.buttonShadowOffsetDark
    } else {
        EnclaveNeoTokens.buttonShadowOffsetLight
    }

/**
 * Solid offset fill behind the node (neo hard shadow, no blur).
 * Apply before [background] so the face paints on top.
 */
fun Modifier.neoHardShadow(
    offset: Dp = EnclaveNeoTokens.buttonShadowOffsetLight,
    color: Color,
    shape: Shape = EnclaveNeoTokens.shape,
): Modifier =
    drawBehind {
        val offsetPx = offset.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        withTransform({ translate(left = offsetPx, top = offsetPx) }) {
            drawPath(path, color)
        }
    }

fun Modifier.neoBorder(
    width: Dp = EnclaveNeoTokens.borderWidth,
    color: Color,
    shape: Shape = EnclaveNeoTokens.shape,
): Modifier = border(width, color, shape)

fun Modifier.neoSurface(
    backgroundColor: Color,
    borderColor: Color,
    withShadow: Boolean = false,
    shadowColor: Color = Color.Black,
    shadowOffset: Dp = EnclaveNeoTokens.buttonShadowOffsetLight,
    shape: Shape = EnclaveNeoTokens.shape,
): Modifier {
    val shadowed =
        if (withShadow) {
            neoHardShadow(offset = shadowOffset, color = shadowColor, shape = shape)
        } else {
            this
        }
    return shadowed.background(backgroundColor, shape).neoBorder(color = borderColor, shape = shape)
}

/**
 * Filled surface with selective edges drawn (e.g. omit top border when flush under an app bar).
 */
fun Modifier.neoSurfaceEdges(
    backgroundColor: Color,
    borderColor: Color,
    shape: Shape = EnclaveNeoTokens.shape,
    drawTop: Boolean = true,
    drawBottom: Boolean = true,
    drawStart: Boolean = true,
    drawEnd: Boolean = true,
    borderWidth: Dp = EnclaveNeoTokens.borderWidth,
): Modifier =
    background(backgroundColor, shape)
        .drawBehind {
            val stroke = borderWidth.toPx()
            val half = stroke / 2f
            if (drawTop) {
                drawLine(
                    borderColor,
                    Offset(0f, half),
                    Offset(size.width, half),
                    strokeWidth = stroke,
                )
            }
            if (drawBottom) {
                drawLine(
                    borderColor,
                    Offset(0f, size.height - half),
                    Offset(size.width, size.height - half),
                    strokeWidth = stroke,
                )
            }
            if (drawStart) {
                drawLine(
                    borderColor,
                    Offset(half, 0f),
                    Offset(half, size.height),
                    strokeWidth = stroke,
                )
            }
            if (drawEnd) {
                drawLine(
                    borderColor,
                    Offset(size.width - half, 0f),
                    Offset(size.width - half, size.height),
                    strokeWidth = stroke,
                )
            }
        }
