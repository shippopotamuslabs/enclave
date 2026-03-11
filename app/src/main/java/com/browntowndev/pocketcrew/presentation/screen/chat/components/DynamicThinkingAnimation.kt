package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


const val SWEEP_DURATION_MS = 1000f      // ← change this for band speed (1000 = 1 second)
const val FULL_CYCLE_MS = 1000           // ← change this for pause time between pulses

/**
 * Pixelated disco-ball style orb animation — ENHANCED (v12: visible rubber-band pulse).
 *
 * Change for your exact request:
 *   - The sweeping band now ONLY uses the 2 darkest oranges (Hot Burnt + Deep Burnt).
 *   - Pixels inside the band still randomly pick between those two → chunky pixelated look.
 *   - The band is now super visible again while keeping the full random cycling outside it.
 */

@Composable
fun DynamicThinkingAnimation(
    modifier: Modifier = Modifier,
) {

    val infiniteTransition = rememberInfiniteTransition(label = "disco_orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse_scale"
    )

    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2800, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "rotation"
    )

    val pulseCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(FULL_CYCLE_MS, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "pulse_cycle"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.scale(pulseScale)) {
        val discoColors = listOf(
            Color(0xFFFF4D00), Color(0xFFFF6F00), Color(0xFFFF8C00),
            Color(0xFFFFB300), Color(0xFFFFD000), Color(0xFFEE6A00),
            Color(0xFFCC4400), Color(0xFFFFFFFF),
        )

        Canvas(modifier = Modifier.size(24.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val orbRadius = size.minDimension / 2 - 3.5f

            drawCircle(color = Color(0xFF0F0F1E), radius = orbRadius, center = center)
            drawCircle(color = Color.Black, radius = orbRadius + 2.2f, center = center, style = Stroke(width = 2.0f))

            val clipPath = Path().apply {
                addOval(Rect(center.x - orbRadius, center.y - orbRadius, center.x + orbRadius, center.y + orbRadius))
            }

            clipPath(clipPath) {
                val gridSize = 15
                val half = (gridSize - 1) / 2f
                val step = (orbRadius * 1.84f) / half
                val pixelSize = step * 1.09f

                for (gy in 0 until gridSize) {
                    for (gx in 0 until gridSize) {
                        val relX = gx - half
                        val relY = gy - half
                        val dist = sqrt(relX * relX + relY * relY)
                        val maxDist = half * 0.94f
                        if (dist > maxDist) continue

                        val normalizedX = relX / half
                        val normalizedY = relY / half

                        // === ROUND RUBBER-BAND PULSE ===
                        val sweepFraction = SWEEP_DURATION_MS / FULL_CYCLE_MS
                        val waveProgress = if (pulseCycle < sweepFraction) {
                            -1.4f + (pulseCycle / sweepFraction) * 2.8f
                        } else 10f
                        val curvedDist = abs(normalizedX - waveProgress) + (normalizedY * normalizedY) * 0.25f
                        val isInPulse = curvedDist < 0.27f && waveProgress < 5f

                        val finalColor = if (isInPulse) {
                            // ← ONLY the 2 darkest colors (chunky pixelated band)
                            val seed = (gx * 137L + gy * 149L + (dist * 23).toLong()) % 10000L.toFloat()
                            val facetSpeed = 0.064f + (seed % 31f) * 0.0013f
                            val time = rotationDeg * 0.82f
                            val colorTime = time * facetSpeed + seed * 0.27f

                            // Randomly pick between the two darkest (index 5 & 6)
                            val darkIndex = if ((colorTime % 2).toInt() == 0) 5 else 6
                            var pixelColor = discoColors[darkIndex]

                            val shimmer = sin((time * 2.9f + seed * 0.9f) * PI.toFloat() / 105f)
                            val brightness = 1f + shimmer * 0.44f

                            val flashTrigger = sin(time * 1.25f + seed * 1.4f)
                            if (flashTrigger > 0.89f && shimmer > 0.58f) {
                                Color.White
                            } else {
                                pixelColor.copy(
                                    red = (pixelColor.red * brightness).coerceAtMost(1f),
                                    green = (pixelColor.green * brightness).coerceAtMost(1f),
                                    blue = (pixelColor.blue * brightness).coerceAtMost(1f)
                                )
                            }
                        } else {
                            // ← Normal random color cycling (outside the pulse)
                            val seed = (gx * 137L + gy * 149L + (dist * 23).toLong()) % 10000L.toFloat()
                            val facetSpeed = 0.064f + (seed % 31f) * 0.0013f
                            val time = rotationDeg * 0.82f
                            val colorTime = time * facetSpeed + seed * 0.27f
                            val colorIndex = ((colorTime % discoColors.size).toInt() % discoColors.size + discoColors.size) % discoColors.size
                            var pixelColor = discoColors[colorIndex]

                            val shimmer = sin((time * 2.9f + seed * 0.9f) * PI.toFloat() / 105f)
                            val brightness = 1f + shimmer * 0.44f

                            val flashTrigger = sin(time * 1.25f + seed * 1.4f)
                            if (flashTrigger > 0.89f && shimmer > 0.58f) Color.White else {
                                pixelColor.copy(
                                    red = (pixelColor.red * brightness).coerceAtMost(1f),
                                    green = (pixelColor.green * brightness).coerceAtMost(1f),
                                    blue = (pixelColor.blue * brightness).coerceAtMost(1f)
                                )
                            }
                        }

                        val px = center.x + relX * step
                        val py = center.y + relY * step

                        drawRoundRect(
                            color = finalColor,
                            topLeft = Offset(px - pixelSize / 2, py - pixelSize / 2),
                            size = Size(pixelSize, pixelSize),
                            cornerRadius = CornerRadius(pixelSize * 0.22f)
                        )
                    }
                }
            }

            // Specular highlight
            val hlAngle = rotationDeg * 1.25f
            val hlX = center.x + sin(hlAngle * PI.toFloat() / 180f) * orbRadius * 0.32f
            val hlY = center.y - cos(hlAngle * PI.toFloat() / 180f) * orbRadius * 0.41f
            drawCircle(
                color = Color.White.copy(alpha = 0.38f),
                radius = orbRadius * 0.33f,
                center = Offset(hlX, hlY),
                blendMode = BlendMode.Screen
            )
        }
    }
}