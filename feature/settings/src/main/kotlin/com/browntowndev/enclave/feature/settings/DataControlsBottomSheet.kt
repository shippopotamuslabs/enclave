package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.browntowndev.enclave.core.ui.component.neo.NeoDestructiveRow
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

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
    val dataRows =
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
        )
    val accountRow =
        DataControlRow(
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
    val rowAlphaModifier =
        Modifier.alpha(if (dc.isDeleting) DELETING_ROW_ALPHA else 1f)

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            NeoSheetHeader(
                title = "Data Controls",
                subtitle = "Manage or permanently remove your data.",
                onDismiss = onDismiss,
            )

            NeoSectionHeader(text = "Data")
            Column(verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing)) {
                dataRows.forEach { row ->
                    NeoDestructiveRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        onClick = row.onRequest,
                        enabled = !dc.isDeleting,
                        modifier = rowAlphaModifier,
                    )
                }
            }

            NeoSectionHeader(text = "Account")
            NeoDestructiveRow(
                title = accountRow.title,
                subtitle = accountRow.subtitle,
                onClick = accountRow.onRequest,
                enabled = !dc.isDeleting,
                isAccountDanger = true,
                modifier = rowAlphaModifier,
            )

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
            text = {
                Column {
                    Text(activeRow.alertBody)
                    if (dc.isDeleting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Deleting...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
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

@Preview(showBackground = true, name = "Data Controls — Light")
@Composable
fun PreviewDataControlsBottomSheetLight() {
    EnclaveTheme(darkTheme = false) {
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
    EnclaveTheme(darkTheme = true) {
        DataControlsBottomSheet(
            uiState =
                MockSettingsData.baseUiState.copy(
                    home = MockSettingsData.baseUiState.home.copy(isDataControlsSheetOpen = true),
                    dataControls = DataControlsUiState(isSheetOpen = true),
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
