package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

private const val DELETING_ROW_ALPHA = 0.4f

private data class DataControlRow(
    val title: String,
    val subtitle: String,
    val alertBody: String,
    val pending: DataControlsPendingAction,
    val onRequest: () -> Unit,
    val onConfirm: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataControlsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onRequestDeleteAllChats: () -> Unit,
    onConfirmDeleteAllChats: () -> Unit,
    onRequestDeleteAllMedia: () -> Unit,
    onConfirmDeleteAllMedia: () -> Unit,
    onRequestDeleteAllMemories: () -> Unit,
    onConfirmDeleteAllMemories: () -> Unit,
    onRequestDeleteAllArtifacts: () -> Unit,
    onConfirmDeleteAllArtifacts: () -> Unit,
    onRequestDeleteAccount: () -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onDismissConfirmation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dc = uiState.dataControls
    val dataRows = listOf(
        DataControlRow(
            title = "Delete all chats",
            subtitle = "Permanently removes all conversations and their messages.",
            alertBody = "All your chats and messages will be permanently deleted. This cannot be undone.",
            pending = DataControlsPendingAction.DeleteChats,
            onRequest = onRequestDeleteAllChats,
            onConfirm = onConfirmDeleteAllChats,
        ),
        DataControlRow(
            title = "Delete all media",
            subtitle = "Permanently removes all generated images, videos, and music.",
            alertBody = "All studio media assets will be permanently deleted. This cannot be undone.",
            pending = DataControlsPendingAction.DeleteMedia,
            onRequest = onRequestDeleteAllMedia,
            onConfirm = onConfirmDeleteAllMedia,
        ),
        DataControlRow(
            title = "Delete all memories",
            subtitle = "Permanently removes all stored memories.",
            alertBody = "All your memories will be permanently deleted. This cannot be undone.",
            pending = DataControlsPendingAction.DeleteMemories,
            onRequest = onRequestDeleteAllMemories,
            onConfirm = onConfirmDeleteAllMemories,
        ),
        DataControlRow(
            title = "Delete all artifacts",
            subtitle = "Permanently removes all generated documents and code files.",
            alertBody = "All generated artifacts and attachments will be permanently deleted. This cannot be undone.",
            pending = DataControlsPendingAction.DeleteArtifacts,
            onRequest = onRequestDeleteAllArtifacts,
            onConfirm = onConfirmDeleteAllArtifacts,
        ),
    )
    val accountRow = DataControlRow(
        title = "Delete account",
        subtitle = "Permanently purges all cloud data and signs you out.",
        alertBody =
            "Your account and all associated cloud data will be permanently deleted. " +
                "You will be signed out. This cannot be undone.",
        pending = DataControlsPendingAction.DeleteAccount,
        onRequest = onRequestDeleteAccount,
        onConfirm = onConfirmDeleteAccount,
    )
    val allRows = dataRows + accountRow

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "Data Controls",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Manage or permanently remove your data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            SectionLabel("Data")
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (dc.isDeleting) DELETING_ROW_ALPHA else 1f),
            ) {
                Column {
                    dataRows.forEachIndexed { index, row ->
                        DataRow(
                            row = row,
                            isDeleting = dc.isDeleting,
                            isDanger = false,
                        )
                        if (index < dataRows.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel(
                text = "Account",
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (dc.isDeleting) DELETING_ROW_ALPHA else 1f),
            ) {
                DataRow(
                    row = accountRow,
                    isDeleting = dc.isDeleting,
                    isDanger = true,
                )
            }

            dc.deleteError?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    val pending = dc.pendingAction
    if (pending != null) {
        val activeRow = allRows.first { it.pending == pending }
        AlertDialog(
            onDismissRequest = { if (!dc.isDeleting) onDismissConfirmation() },
            properties =
                DialogProperties(
                    dismissOnBackPress = !dc.isDeleting,
                    dismissOnClickOutside = !dc.isDeleting,
                ),
            title = { Text("Are you sure?") },
            text = { Text(activeRow.alertBody) },
            confirmButton = {
                TextButton(
                    onClick = activeRow.onConfirm,
                    enabled = !dc.isDeleting,
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissConfirmation,
                    enabled = !dc.isDeleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun DataRow(
    row: DataControlRow,
    isDeleting: Boolean,
    isDanger: Boolean,
) {
    val titleColor = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconTint = MaterialTheme.colorScheme.error
    val icon = if (isDanger) Icons.Default.DeleteForever else Icons.Default.Delete

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isDeleting, onClick = row.onRequest)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .padding(end = 0.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Data Controls — Light")
@Composable
fun PreviewDataControlsBottomSheetLight() {
    PocketCrewTheme(darkTheme = false) {
        DataControlsBottomSheet(
            uiState =
                MockSettingsData.baseUiState.copy(
                    home = MockSettingsData.baseUiState.home.copy(isDataControlsSheetOpen = true),
                    dataControls =
                        DataControlsUiState(
                            isSheetOpen = true,
                        ),
                ),
            onDismiss = {},
            onRequestDeleteAllChats = {},
            onConfirmDeleteAllChats = {},
            onRequestDeleteAllMedia = {},
            onConfirmDeleteAllMedia = {},
            onRequestDeleteAllMemories = {},
            onConfirmDeleteAllMemories = {},
            onRequestDeleteAllArtifacts = {},
            onConfirmDeleteAllArtifacts = {},
            onRequestDeleteAccount = {},
            onConfirmDeleteAccount = {},
            onDismissConfirmation = {},
        )
    }
}

@Preview(showBackground = true, name = "Data Controls — Dark")
@Composable
fun PreviewDataControlsBottomSheetDark() {
    PocketCrewTheme(darkTheme = true) {
        DataControlsBottomSheet(
            uiState =
                MockSettingsData.baseUiState.copy(
                    home = MockSettingsData.baseUiState.home.copy(isDataControlsSheetOpen = true),
                    dataControls =
                        DataControlsUiState(
                            isSheetOpen = true,
                            isDeleting = true,
                            pendingAction = DataControlsPendingAction.DeleteChats,
                        ),
                ),
            onDismiss = {},
            onRequestDeleteAllChats = {},
            onConfirmDeleteAllChats = {},
            onRequestDeleteAllMedia = {},
            onConfirmDeleteAllMedia = {},
            onRequestDeleteAllMemories = {},
            onConfirmDeleteAllMemories = {},
            onRequestDeleteAllArtifacts = {},
            onConfirmDeleteAllArtifacts = {},
            onRequestDeleteAccount = {},
            onConfirmDeleteAccount = {},
            onDismissConfirmation = {},
        )
    }
}
