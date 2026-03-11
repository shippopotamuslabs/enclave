package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExperimentalThinkingIndicator(
    thinkingSteps: List<String>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))
    ) {
        // Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            GlowingEmberOrb()

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Thinking",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            val rotation by animateFloatAsState(
                targetValue = if (isExpanded) -90f else 0f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                label = "arrow_rotation"
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }

        // Expanded Thinking Steps
        AnimatedVisibility(
            visible = isExpanded && thinkingSteps.isNotEmpty(),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            val listState = rememberLazyListState()

            // Auto-scroll to the bottom when new items are added
            LaunchedEffect(thinkingSteps.size) {
                if (thinkingSteps.isNotEmpty()) {
                    listState.animateScrollToItem(thinkingSteps.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .padding(top = 12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = thinkingSteps,
                    key = { it.hashCode() }
                ) { step ->
                    val isLatest = step == thinkingSteps.last()
                    ThoughtBubble(
                        stepText = step,
                        isLatest = isLatest,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun GlowingEmberOrb(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .scale(scale)
            .alpha(alpha)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF5A00), // Bright orange-red core
                        Color(0xFFFFA500), // Amber middle
                        Color(0xFFFF4500).copy(alpha = 0.5f), // Outer glow with alpha
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    ) {
        // Core inner highlight for extra depth
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = Color.White.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .alpha(alpha)
        )
    }
}

@Composable
private fun ThoughtBubble(
    stepText: String,
    isLatest: Boolean,
    modifier: Modifier = Modifier
) {
    // Parse AgentName: Message
    val parts = stepText.split(":", limit = 2)
    val agentName = if (parts.size == 2) parts[0].trim() else "Agent"
    val message = if (parts.size == 2) parts[1].trim() else stepText.trim()

    val bubbleAlpha = if (isLatest) 1.0f else 0.75f
    val textColor = if (isLatest) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp) // Align slightly indented under the orb
            .alpha(bubbleAlpha)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Agent Name Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = agentName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Message Text
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}

// --- Previews ---

// Dummy theme wrapper to allow standalone compilation for previews.
// In actual usage, this relies on the real PocketCrewTheme from your codebase.
@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_EmptyPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingSteps = emptyList(),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_SingleStepPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingSteps = listOf(
                "Harper: Initializing search strategy for deep dive analysis."
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ThinkingIndicator_MultipleStepsPreview() {
    PreviewTheme {
        ThinkingIndicator(
            thinkingSteps = listOf(
                "Lucas: Planning task execution and parallel searches.",
                "Harper: Executing web_search for latest documentation.",
                "Benjamin: Synthesizing results and structuring the final report."
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}