package com.browntowndev.pocketcrew.feature.chat.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.feature.chat.ThinkingDataUi
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.component.markdown.StreamingMarkdownText
import kotlinx.coroutines.launch

// ── Main composable ──

@Composable
fun AssistantResponse(
    modifier: Modifier = Modifier,
    message: ChatMessage,
) {
    // Get content and pipeline step from ContentUi
    val contentText = message.content.text
    val pipelineStep = message.content.pipelineStep

    // State for step completion bottom sheet
    var showStepDetails by remember { mutableStateOf(false) }
    var selectedStepName by remember { mutableStateOf("") }
    var selectedStepOutput by remember { mutableStateOf("") }

    // Show bottom sheet if triggered
    StepCompletionBottomSheet(
        isVisible = showStepDetails,
        stepName = selectedStepName,
        stepOutput = selectedStepOutput,
        onDismiss = { showStepDetails = false }
    )

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
                    selectedStepOutput = contentText
                    showStepDetails = true
                }
            )
        } else {
            // Response content - use StreamingMarkdownText for proper markdown rendering
            StreamingMarkdownText(
                markdown = contentText,
                modifier = Modifier.fillMaxWidth(),
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

// ── Bottom sheet for step completion details ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCompletionBottomSheet(
    isVisible: Boolean,
    stepName: String,
    stepOutput: String,
    onDismiss: () -> Unit,
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with step name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$stepName Output",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step output content
                SelectionContainer {
                    Text(
                        text = stepOutput,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

