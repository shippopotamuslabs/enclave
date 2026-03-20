package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.core.ui.component.ShimmerText
import kotlinx.coroutines.delay

/**
 * Redesigned Thinking Indicator that matches the Grok-style thinking indicator.
 *
 * Features:
 * - Dynamic molten-lava orb with constantly swirling animation
 * - "Thinking" text with elapsed time counter
 * - Chevron that rotates 90° when tapped to reveal details in bottom sheet
 *
 * @param thinkingRaw Raw thinking text as markdown
 * @param thinkingStartTime Timestamp (System.currentTimeMillis()) when thinking started. If not provided or 0, elapsed time won't be shown.
 * @param modifier Modifier for the composable
 * @param isExpanded Whether the details are expanded (chevron rotation)
 * @param onToggleDetails Callback when the indicator is tapped to toggle details bottom sheet
 */
@Composable
fun ThinkingIndicator(
    thinkingRaw: String,
    modifier: Modifier = Modifier,
    thinkingStartTime: Long = 0L,
    modelDisplayName: String = "",
    isExpanded: Boolean = false,
    onToggleDetails: () -> Unit = {},
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    // Track elapsed thinking time
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Update elapsed time every second when thinking is active
    LaunchedEffect(thinkingRaw.isNotEmpty()) {
        if (thinkingRaw.isNotEmpty()) {
            // Calculate initial elapsed time if start time provided
            if (thinkingStartTime > 0) {
                elapsedSeconds = ((System.currentTimeMillis() - thinkingStartTime) / 1000).toInt()
            }

            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    Column(
        modifier = modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
    ) {
        // Header Row: Orb + "Thinking" text + elapsed time + chevron
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleDetails() }
                .padding(vertical = 8.dp)
        ) {
            // Dynamic molten-lava orb
            DynamicThinkingAnimation(
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(5.dp))

            // "Thinking - Xs" text with shimmer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val thinkingText = if (elapsedSeconds > 0 || thinkingStartTime > 0) {
                    "Thinking • ${formatElapsedTime(elapsedSeconds)}"
                } else {
                    "Thinking"
                }

                val grayColor = MaterialTheme.colorScheme.onSurfaceVariant
                val highlightColor = grayColor.copy(alpha = 0.3f)

                ShimmerText(
                    text = thinkingText,
                    baseColor = grayColor,
                    highlightColor = highlightColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                )

                Spacer(Modifier.width(6.dp))
                // Chevron that rotates when tapped
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand thinking details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

/**
 * Formats elapsed seconds into a human-readable string.
 */
private fun formatElapsedTime(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

// --- Previews ---

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_EmptyPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingRaw = "",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_WithContentPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingRaw = "Analyzing the problem step by step:\n\n1. First, let me understand what we're dealing with\n2. Breaking down the requirements\n3. Planning the implementation",
            modifier = Modifier.padding(16.dp)
        )
    }
}
