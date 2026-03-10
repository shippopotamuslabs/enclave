package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme

@Composable
fun ThinkingIndicator(
    thinkingSteps: List<String>,
    modifier: Modifier = Modifier,
) {
    var stepsExpanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (stepsExpanded) -90f else 0f,
        label = "thinking_steps_arrow_rotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .animateContentSize(),
    ) {
        // Shimmer "Thinking" label — tap to expand/collapse steps
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                enabled = thinkingSteps.isNotEmpty(),
                onClick = { stepsExpanded = !stepsExpanded },
            ),
        ) {
            ShimmerText(
                text = "Thinking",
                baseColor = MaterialTheme.colorScheme.primary,
                highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )

            if (thinkingSteps.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (stepsExpanded) "Collapse thinking steps"
                    else "Expand thinking steps",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
            }
        }

        // Expandable agent step feed
        AnimatedVisibility(
            visible = stepsExpanded && thinkingSteps.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(top = 6.dp),
            ) {
                thinkingSteps.forEachIndexed { index, step ->
                    val isLatest = index == thinkingSteps.lastIndex
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = if (isLatest) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    top = if (index == 0) 0.dp else 3.dp,
                                ),
                        )
                    }
                }
            }
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewThinkingShimmerOnly() {
    PocketCrewTheme {
        ThinkingIndicator(thinkingSteps = emptyList())
    }
}

@Preview
@Composable
private fun PreviewThinkingWithSteps() {
    PocketCrewTheme(darkTheme = true) {
        ThinkingIndicator(
            thinkingSteps = listOf(
                "Agent A: Drafting direct answer...",
                "Agent B: Generating skeptical critique...",
                "Synthesizing Draft 1 + Draft 2...",
                "Refinement round 1: checking for contradictions",
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewThinkingSingleStep() {
    PocketCrewTheme {
        ThinkingIndicator(
            thinkingSteps = listOf("Analyzing query..."),
        )
    }
}
