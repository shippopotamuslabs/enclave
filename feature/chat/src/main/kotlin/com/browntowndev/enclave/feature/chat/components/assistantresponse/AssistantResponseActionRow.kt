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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryIconButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTextButton
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
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
                EnclaveTextButton(
                    text = "Report",
                    onClick = {
                        onReportClick(message.id)
                        showReportDialog = false
                    },
                )
            },
            dismissButton = {
                EnclaveTextButton(
                    text = "Cancel",
                    onClick = { showReportDialog = false },
                )
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
                EnclaveSecondaryIconButton(
                    onClick = { onShareClick(message.id) },
                    icon = Icons.Default.Share,
                    contentDescription = "Share",
                    size = ActionIconButtonSize,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )

                EnclaveSecondaryIconButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Enclave Response", contentText)),
                            )
                        }
                    },
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "Copy response",
                    size = ActionIconButtonSize,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (hasTtsProviderAssigned) {
                    when {
                        isLoadingTts -> {
                            Box(
                                modifier = Modifier.size(ActionIconButtonSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                EnclaveCircularProgressIndicator(
                                    modifier = Modifier.size(ActionIconSize),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        isPlayingTts -> {
                            EnclaveSecondaryIconButton(
                                onClick = onPauseAudio,
                                icon = Icons.Filled.Pause,
                                contentDescription = "Pause audio",
                                size = ActionIconButtonSize,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        isPausedTts -> {
                            EnclaveSecondaryIconButton(
                                onClick = { onPlayAudio(contentText) },
                                icon = Icons.Filled.PlayArrow,
                                contentDescription = "Resume audio",
                                size = ActionIconButtonSize,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            EnclaveSecondaryIconButton(
                                onClick = { onPlayAudio(contentText) },
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read aloud",
                                size = ActionIconButtonSize,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (message.webSearchSources.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .enclaveSurface(
                                    backgroundColor =
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    borderColor = enclaveBorderColor(),
                                    shape = RoundedCornerShape(16.dp),
                                ).clickable { onShowSources() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box {
                    EnclaveSecondaryIconButton(
                        onClick = { overflowExpanded = true },
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More response actions",
                        size = ActionIconButtonSize,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
