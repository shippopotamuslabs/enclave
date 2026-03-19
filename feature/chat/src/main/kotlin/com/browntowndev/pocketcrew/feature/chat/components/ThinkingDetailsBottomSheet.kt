package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.component.markdown.StreamingMarkdownText


/**
 * Bottom sheet that displays the full thinking details as markdown.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ThinkingDetailsBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    thinkingDurationSeconds: Long,
    thinkingRaw: String,
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
                        tint = MaterialTheme.colorScheme.onSurface,
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

                // Raw thinking content rendered as markdown
                if (thinkingRaw.isNotBlank()) {
                    StreamingMarkdownText(
                        markdown = thinkingRaw,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        text = "No thinking content available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatThinkingDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

@Preview(showBackground = true)
@Composable
private fun PreviewThinkingDetailsBottomSheet() {
    PocketCrewTheme {
        ThinkingDetailsBottomSheet(
            isVisible = true,
            thinkingRaw = """
                # Analysis in Progress

                Let me break down this problem:

                1. First, identify the key components
                2. Research the historical context
                3. Synthesize a comprehensive response

                ```kotlin
                fun main() {
                    println("Hello!")
                }
                ```
            """.trimIndent(),
            thinkingDurationSeconds = 42,
            modelDisplayName = "PocketCrew Assistant",
            onDismiss = {}
        )
    }
}
