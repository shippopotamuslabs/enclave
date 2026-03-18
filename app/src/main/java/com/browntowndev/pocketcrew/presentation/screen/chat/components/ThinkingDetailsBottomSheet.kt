package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.R
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme


/**
 * Bottom sheet that displays the full thinking details with ThoughtBubble components.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ThinkingDetailsBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    thinkingDurationSeconds: Long,
    thinkingSteps: List<String>,
    modelDisplayName: String
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.lightbulb
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Thought for ${formatThinkingDuration(thinkingDurationSeconds)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Thought bubbles - let LazyColumn size naturally based on content
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(thinkingSteps) { _, step ->
                        ThoughtBubbleStatic(
                            stepText = step,
                            modelDisplayName = modelDisplayName
                        )
                    }
                }
            }
        }
    }
}

fun formatThinkingDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

/**
 * Static thought bubble for bottom sheet (without animations).
 */
@Composable
private fun ThoughtBubbleStatic(
    stepText: String,
    modelDisplayName: String,
    modifier: Modifier = Modifier
) {
    val displayName = modelDisplayName.ifBlank { "Agent" }

    Row(
        modifier = modifier.fillMaxWidth()
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
                        text = stepText,
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

@Preview(showBackground = true)
@Composable
private fun PreviewThinkingDetailsBottomSheet() {
    PocketCrewTheme {
        ThinkingDetailsBottomSheet(
            isVisible = true,
            thinkingSteps = listOf(
                "Analyzing the user's request for information about quantum computing.",
                "Searching the internal knowledge base for recent breakthroughs in topological superconductors.",
                "Synthesizing a comprehensive explanation suitable for a beginner audience.",
                "Double-checking the accuracy of the historical timeline provided."
            ),
            thinkingDurationSeconds = 42,
            modelDisplayName = "PocketCrew Assistant",
            onDismiss = {}
        )
    }
}
