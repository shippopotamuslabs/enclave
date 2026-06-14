package com.browntowndev.enclave.feature.chat.components.assistantresponse

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.neo.NeoCircularProgressIndicator
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ChatMessage
import kotlinx.coroutines.launch

private val ActionIconButtonSize = 32.dp
private val ActionIconSize = 18.dp

@Composable
internal fun AssistantResponseActionRow(
    message: ChatMessage,
    contentText: String,
    isComplete: Boolean,
    hasTtsProviderAssigned: Boolean,
    isPlayingTts: Boolean,
    isLoadingTts: Boolean,
    isPausedTts: Boolean,
    onPlayAudio: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onShareClick: (MessageId) -> Unit,
    onShowSources: () -> Unit,
    onStatsClick: (MessageId) -> Unit = {},
    onReportClick: (MessageId) -> Unit = {},
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showReportDialog by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Response") },
            text = {
                Text("Submit feedback that this response should have been blocked by moderation?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReportClick(message.id)
                        showReportDialog = false
                    },
                ) {
                    Text("Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (isComplete) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(
                    onClick = { onShareClick(message.id) },
                    modifier = Modifier.size(ActionIconButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(ActionIconSize),
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Enclave Response", contentText)),
                            )
                        }
                    },
                    modifier = Modifier.size(ActionIconButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy response",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (hasTtsProviderAssigned) {
                    when {
                        isLoadingTts -> {
                            Box(
                                modifier = Modifier.size(ActionIconButtonSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                NeoCircularProgressIndicator(
                                    modifier = Modifier.size(ActionIconSize),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        isPlayingTts -> {
                            IconButton(
                                onClick = onPauseAudio,
                                modifier = Modifier.size(ActionIconButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "Pause audio",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ActionIconSize),
                                )
                            }
                        }
                        isPausedTts -> {
                            IconButton(
                                onClick = { onPlayAudio(contentText) },
                                modifier = Modifier.size(ActionIconButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Resume audio",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ActionIconSize),
                                )
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = { onPlayAudio(contentText) },
                                modifier = Modifier.size(ActionIconButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read aloud",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ActionIconSize),
                                )
                            }
                        }
                    }
                }

                if (message.webSearchSources.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .clickable { onShowSources() },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sources",
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    ),
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.size(ActionIconButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More response actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ActionIconSize),
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Stats") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.BarChart,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onStatsClick(message.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Report") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                showReportDialog = true
                            },
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.size(ActionIconButtonSize))
        }

        if (isComplete && message.formattedTimestamp.isNotBlank()) {
            Text(
                text = message.formattedTimestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
