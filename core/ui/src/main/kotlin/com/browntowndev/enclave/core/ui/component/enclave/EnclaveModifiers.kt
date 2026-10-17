package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeAccessor
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun enclaveBorderColor(): Color = EnclaveThemeAccessor.colors.borderStrong

@Composable
fun enclaveShadowColor(): Color = EnclaveThemeAccessor.elevations.hardShadowColor

/**
 * Blur-over-content effect driven by the active spec's glass tokens.
 * No-op when the spec disables glass or no [hazeState] is supplied, so callers
 * render solid chrome under opaque specs without branching on theme family.
 */
@Composable
fun Modifier.enclaveGlassEffect(hazeState: HazeState?): Modifier {
    val glass = EnclaveThemeAccessor.glass
    if (!glass.enabled || hazeState == null) {
        return this
    }
    return hazeEffect(state = hazeState) {
        blurRadius = glass.blurRadius
        tints = listOf(HazeTint(glass.tint))
        noiseFactor = glass.noiseFactor
    }
}

@Composable
fun enclaveButtonShadowOffset(): Dp =
    if (EnclaveThemeAccessor.elevations.useHardShadow) {
        EnclaveThemeAccessor.elevations.buttonShadowOffset
    } else {
        0.dp
    }

/**
 * Solid offset fill behind the node (neo hard shadow, no blur).
 * Apply before [background] so the face paints on top.
 */
fun Modifier.enclaveHardShadow(
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

fun Modifier.enclaveBorder(
    width: Dp = EnclaveNeoTokens.borderWidth,
    color: Color,
    shape: Shape = EnclaveNeoTokens.shape,
): Modifier = border(width, color, shape)

@Composable
fun Modifier.enclaveSurface(
    backgroundColor: Color,
    borderColor: Color,
    withShadow: Boolean = false,
    shadowColor: Color = EnclaveThemeAccessor.elevations.hardShadowColor,
    shadowOffset: Dp = EnclaveThemeAccessor.elevations.buttonShadowOffset,
    shape: Shape = EnclaveThemeAccessor.shapes.panel,
): Modifier {
    val elevations = EnclaveThemeAccessor.elevations
    val shadowed =
        if (withShadow && elevations.useHardShadow) {
            enclaveHardShadow(offset = shadowOffset, color = shadowColor, shape = shape)
        } else {
            this
        }
    return shadowed
        .background(backgroundColor, shape)
        .enclaveBorder(width = EnclaveThemeAccessor.shapes.borderWidth, color = borderColor, shape = shape)
}

/**
 * Filled surface with selective edges drawn (e.g. omit top border when flush under an app bar).
 */
@Composable
fun Modifier.enclaveSurfaceEdges(
    backgroundColor: Color,
    borderColor: Color,
    shape: Shape = EnclaveThemeAccessor.shapes.panel,
    drawTop: Boolean = true,
    drawBottom: Boolean = true,
    drawStart: Boolean = true,
    drawEnd: Boolean = true,
    borderWidth: Dp = EnclaveThemeAccessor.shapes.borderWidth,
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
