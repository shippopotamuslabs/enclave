package com.browntowndev.pocketcrew.core.ui.component.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Confirmation dialog the user must accept before [com.browntowndev.pocketcrew.domain.usecase
 * .security.AdoptCloudVaultUseCase] runs. The adopter is destructive — it wipes all local Room
 * data — so silent execution is forbidden.
 *
 * @param isReconciling when true, the confirm and cancel buttons disable and a spinner replaces
 *   the confirm-button label so the user knows the clobber is in flight.
 */
@Composable
fun VaultMismatchConfirmDialog(
    isReconciling: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isReconciling) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = !isReconciling,
            dismissOnClickOutside = !isReconciling,
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
        },
        title = {
            Text(
                text = "Replace local data with cloud?",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This device's encryption key does not match the one stored in your account.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Continuing will erase all local chats, memories, and studio media on this " +
                        "device, then re-download everything from your account. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Cancel keeps your local data intact and skips cloud sync for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isReconciling,
            ) {
                if (isReconciling) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(0.dp))
                        Text(
                            text = "  Replacing...",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        text = "Replace local",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isReconciling,
            ) {
                Text("Cancel")
            }
        },
    )
}
