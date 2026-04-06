package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.core.ui.component.markdown.StreamableMarkdownText

// ── Main composable ──

@Composable
fun AssistantResponse(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    isPreview: Boolean = false,
) {
    // Get content and pipeline step from ContentUi
    val contentText = message.content.text
    val pipelineStep = message.content.pipelineStep

    // State for step completion bottom sheet
    var showStepDetails by remember { mutableStateOf(false) }
    var selectedStepName by remember { mutableStateOf("") }

    // Show bottom sheet if triggered
    if (showStepDetails) {
        DetailBottomSheet(
            config = DetailBottomSheetConfig.StepCompletion(
                isVisible = showStepDetails,
                content = contentText, // Always use live content from message
                stepName = selectedStepName,
                onDismiss = { showStepDetails = false }
            )
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // For Crew mode: if this message has a pipeline step (non-FINAL), render as completed step
        // For FINAL or null pipelineStep: render as regular message content
        val isCompletedStep = pipelineStep != null && pipelineStep != PipelineStep.FINAL

        if (isCompletedStep) {
            // Render as completed step row
            CompletedStepRow(
                stepName = pipelineStep.displayName(),
                stepOutput = contentText,
                modelDisplayName = message.modelDisplayName,
                onClick = {
                    selectedStepName = pipelineStep.displayName()
                    showStepDetails = true
                }
            )
        } else {
            // Response content - use StreamingMarkdownText for proper markdown rendering
            // disable scroll since parent LazyColumn handles scrolling
            StreamableMarkdownText(
                markdown = contentText,
                modifier = Modifier.fillMaxWidth(),
                enableScroll = false,
                isStreaming = true,
                isPreview = isPreview, // So preview text is rendered
            )

            // Timestamp
            Text(
                text = message.formattedTimestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    .align(Alignment.End),
            )
        }
    }
}

// ── Single completed step row (used for interleaved rendering) ──

@Composable
private fun CompletedStepRow(
    stepName: String,
    stepOutput: String,
    modelDisplayName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (stepOutput.isEmpty()) return

    // Preview text - "Tap to View" as requested
    val previewText = "Tap to View"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Model message: "$modelDisplayName: I have completed my task for the Crew. Passing on to the next member."
        val completionMessage = remember(modelDisplayName) {
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(modelDisplayName)
                }
                append(": I have completed my task for the Crew. Passing on to the next member.")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = completionMessage,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .clickable { onClick() }
                .padding(horizontal = 12.dp)
        ) {
            // Document icon - Rectangular, rotated 10 degrees, shifted down slightly
            // Position extends below card so bottom gets clipped by container
            Box(
                modifier = Modifier
                    .offset(y = 12.dp)
                    .padding(start = 12.dp)
                    .rotate(-10f)
                    .size(width = 60.dp, height = 90.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Text content on the right - moved further right
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$stepName Results",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}



