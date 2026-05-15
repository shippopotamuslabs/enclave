package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

private const val DELETING_ROW_ALPHA = 0.5f

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
    val rows =
        listOf(
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
            DataControlRow(
                title = "Delete account",
                subtitle = "Permanently purges all cloud data and signs you out.",
                alertBody =
                    "Your account and all associated cloud data will be permanently deleted. " +
                        "You will be signed out. This cannot be undone.",
                pending = DataControlsPendingAction.DeleteAccount,
                onRequest = onRequestDeleteAccount,
                onConfirm = onConfirmDeleteAccount,
            ),
        )

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Data Controls",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(rows, key = { it.title }) { row ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .alpha(if (dc.isDeleting) DELETING_ROW_ALPHA else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !dc.isDeleting, onClick = row.onRequest),
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(row.title) },
                            supportingContent = { Text(row.subtitle) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                }
            }
            dc.deleteError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    val pending = dc.pendingAction
    if (pending != null) {
        val activeRow = rows.first { it.pending == pending }
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
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
                TextButton(onClick = onDismissConfirmation) {
                    Text("Cancel")
                }
            },
        )
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
