package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Redesigned Thinking Indicator that matches the Grok-style thinking indicator.
 *
 * Features:
 * - Dynamic liquid-flow / molten-lava orb with constantly swirling, ebbing, color-shifting interior
 * - "Thinking" text with elapsed time counter
 * - Exactly 3 visible thought bubbles with smooth vertical wheel animation
 * - Self-contained, always expanded while thinking is active
 *
 * @param thinkingSteps List of thought messages to display
 * @param thinkingStartTime Timestamp (System.currentTimeMillis()) when thinking started. If not provided or 0, elapsed time won't be shown.
 * @param modifier Modifier for the composable
 */
@Composable
fun ThinkingIndicator(
    thinkingSteps: List<String>,
    modifier: Modifier = Modifier,
    thinkingStartTime: Long = 0L,
    modelDisplayName: String = "",
) {
    // Track elapsed thinking time
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Update elapsed time every second when thinking is active
    LaunchedEffect(thinkingSteps.isNotEmpty()) {
        if (thinkingSteps.isNotEmpty()) {
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
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Header Row: Orb + "Thinking" + elapsed time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Dynamic molten-lava orb
            DynamicThinkingAnimation(
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(5.dp))

            // Combined "Thinking - Xs" text with unified shimmer
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val thinkingText = if (elapsedSeconds > 0 || thinkingStartTime > 0) {
                    "Thinking • ${formatElapsedTime(elapsedSeconds)}"
                } else {
                    "Thinking"
                }

                // Gray shimmer text
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
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Thought bubbles section - exactly 3 visible with wheel animation
        if (thinkingSteps.isNotEmpty()) {
            ThoughtBubbleWheel(
                thoughts = thinkingSteps,
                modelDisplayName = modelDisplayName,
                modifier = Modifier.fillMaxWidth()
            )
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

/**
 * Thought bubble wheel with smooth vertical scrolling animation.
 * Shows exactly 3 bubbles at a time with wheel-like rotation effect.
 * Uses buffering to debounce rapid thought updates for smooth animations.
 */
@Composable
private fun ThoughtBubbleWheel(
    thoughts: List<String>,
    modelDisplayName: String,
    modifier: Modifier = Modifier
) {
    var displayedThoughts by remember { mutableStateOf<List<ThoughtItem>>(emptyList()) }
    var pendingThoughts by remember { mutableStateOf<List<Triple<Int, String, Int>>>(emptyList()) }

    var lastProcessedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(thoughts) {
        if (thoughts.isEmpty()) {
            displayedThoughts = emptyList()
            pendingThoughts = emptyList()
            lastProcessedCount = 0
            return@LaunchedEffect
        }

        if (thoughts.size > lastProcessedCount) {
            for (i in lastProcessedCount until thoughts.size) {
                val pendingIndex = pendingThoughts.size
                pendingThoughts = pendingThoughts + Triple(i, thoughts[i], pendingIndex)
            }
        }
        lastProcessedCount = thoughts.size

        if (pendingThoughts.isNotEmpty()) {
            delay(450L)

            val (globalIndex, nextText, _) = pendingThoughts.first()
            pendingThoughts = pendingThoughts.drop(1)

            val newItem = ThoughtItem(
                id = globalIndex.toString(),
                text = nextText,
                isNewest = true
            )

            displayedThoughts = (displayedThoughts.map { it.copy(isNewest = false) } + newItem).takeLast(3)
        }
    }

    LazyColumn(
        modifier = modifier.heightIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(
            items = displayedThoughts,
            key = { it.id }
        ) { item ->
            AnimatedThoughtBubble(
                item = item,
                modelDisplayName = modelDisplayName
            )
        }
    }
}

/**
 * Data class to represent a thought item with stable identity.
 */
private data class ThoughtItem(
    val id: String,
    val text: String,
    val isNewest: Boolean
)

/**
 * Animated thought bubble that slides in from bottom with wheel effect.
 * Uses offset animation for entrance and preserves existing scale/alpha animations.
 */
@Composable
private fun LazyItemScope.AnimatedThoughtBubble(
    item: ThoughtItem,
    modelDisplayName: String,
    modifier: Modifier = Modifier
) {
    val entranceAnimatable = remember(item.id) {
        Animatable(if (item.isNewest) 140f else 0f)
    }

    LaunchedEffect(item.id) {
        entranceAnimatable.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 420,
                easing = FastOutSlowInEasing
            )
        )
    }

    ThoughtBubble(
        stepText = item.text,
        isNewest = item.isNewest,
        modelDisplayName = modelDisplayName,
        modifier = modifier
            .offset(y = entranceAnimatable.value.dp)
            .animateItem(
                placementSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                )
            )
    )
}

@Composable
private fun ThoughtBubble(
    stepText: String,
    isNewest: Boolean,
    modelDisplayName: String,
    modifier: Modifier = Modifier
) {
    val displayName = modelDisplayName.ifBlank { "Agent" }
    val message = stepText

    val alpha by animateFloatAsState(
        targetValue = if (isNewest) 1f else 0.85f,
        animationSpec = tween(300),
        label = "bubble_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubble_scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .scale(scale)
            .padding(start = 8.dp)
    ) {
        Column {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        }
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
            thinkingSteps = emptyList(),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_SingleStepPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingSteps = listOf(
                "Overclocked Truth Nuke: Initializing search strategy for deep dive analysis."
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_MultipleStepsPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingSteps = listOf(
                "Overclocked Truth Nuke: Planning task execution and parallel searches.",
                "Overclocked Truth Nuke: Executing web_search for latest documentation.",
                "Overclocked Truth Nuke: Synthesizing results and structuring the final report."
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_ManyStepsPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingSteps = listOf(
                "Overclocked Truth Nuke: Planning task execution and parallel searches.",
                "Overclocked Truth Nuke: Executing web_search for latest documentation.",
                "Overclocked Truth Nuke: Synthesizing results and structuring the final report.",
                "Overclocked Truth Nuke: Analyzing the search results for relevant patterns.",
                "Overclocked Truth Nuke: Cross-referencing with internal knowledge base.",
                "Overclocked Truth Nuke: Preparing final summary with citations."
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}