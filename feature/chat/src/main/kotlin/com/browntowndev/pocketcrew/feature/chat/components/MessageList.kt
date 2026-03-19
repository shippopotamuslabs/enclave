package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.feature.chat.ThinkingDataUi
import com.browntowndev.pocketcrew.feature.chat.components.formatThinkingDuration
import com.browntowndev.pocketcrew.feature.chat.fakeLongMessages
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    val hasActiveIndicator = messages.any { it.indicatorState != null }

    if (messages.isEmpty() && !hasActiveIndicator) {
        EmptyState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 16.dp),
        ) {
            items(
                count = messages.size,
                key = { index: Int -> "msg_${messages[messages.size - 1 - index].id}" },
            ) { index: Int ->
                val messageIndex = messages.size - 1 - index
                val message = messages[messageIndex]

                // ============================================================
                // Indicator rendering - shows at TOP for Assistant messages
                // (below content for User messages since they have no indicators)
                // ============================================================
                Indicators(
                    modelDisplayName = message.modelDisplayName,
                    indicatorState = message.indicatorState
                )

                // ============================================================
                // Content rendering based on message role
                // ============================================================
                if (message.role == MessageRole.User) {
                    MessageBubble(message = message)
                } else {
                    AssistantResponse(message = message)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Icon(
            painter = painterResource(R.drawable.pocket_crew_icon),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
        )
        Text(
            text = "Pocket Crew is ready",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Ask us anything",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Shows the appropriate indicator(s) based on the message's indicator state.
 */
@Composable
private fun Indicators(modelDisplayName: String, indicatorState: IndicatorState?) {
    if (indicatorState != null) {
        var bottomSheetThinkingData by remember { mutableStateOf<ThinkingDataUi?>(null) }

        when (indicatorState) {
            is IndicatorState.Thinking -> {
                ThinkingIndicator(
                    thinkingSteps = indicatorState.thinkingSteps,
                    thinkingStartTime = indicatorState.thinkingDurationSeconds,
                    modelDisplayName = modelDisplayName,
                )
            }
            is IndicatorState.Processing -> {
                ProcessingIndicator()
            }
            is IndicatorState.Generating -> {
                val thinkingData = indicatorState.thinkingData
                if (thinkingData != null && thinkingData.steps.isNotEmpty()) {
                    ThoughtForHeader(
                        thinkingData = thinkingData,
                        onViewFullThinking = {
                            bottomSheetThinkingData = indicatorState.thinkingData
                        }
                    )
                }
                GeneratingIndicator()
            }
            is IndicatorState.Complete -> {
                val thinkingData = indicatorState.thinkingData
                if (thinkingData != null && thinkingData.steps.isNotEmpty()) {
                    ThoughtForHeader(
                        thinkingData = thinkingData,
                        onViewFullThinking = {
                            bottomSheetThinkingData = indicatorState.thinkingData
                        }
                    )
                }
            }
            is IndicatorState.None -> {
                // Show no indicator at all.
            }
        }

        ThinkingDetailsBottomSheet(
            isVisible = bottomSheetThinkingData != null,
            thinkingSteps = bottomSheetThinkingData?.steps ?: emptyList(),
            thinkingDurationSeconds = bottomSheetThinkingData?.thinkingDurationSeconds ?: 0,
            modelDisplayName = modelDisplayName,
            onDismiss = { bottomSheetThinkingData = null }
        )
    }
}

@Composable
private fun ThoughtForHeader(
    modifier: Modifier = Modifier,
    thinkingData: ThinkingDataUi,
    onViewFullThinking: () -> Unit,
) {
    val durationText = formatThinkingDuration(thinkingData.thinkingDurationSeconds)

    // Header row: lightbulb + "Thought for Xs" — clickable to open bottom sheet
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewFullThinking() }
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.lightbulb),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Thought for $durationText",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── "Thought for Xs" header that opens bottom sheet ──

private fun formatThinkingDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewMessageListEmpty() {
    PocketCrewTheme {
        MessageList(emptyList())
    }
}

@Preview
@Composable
private fun PreviewMessageListLong() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(fakeLongMessages)
    }
}
