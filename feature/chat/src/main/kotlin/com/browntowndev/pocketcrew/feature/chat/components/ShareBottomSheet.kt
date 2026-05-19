package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.feature.chat.ShareState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    shareState: ShareState,
    onShareTurn: () -> Unit,
    onShareFullChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (shareState is ShareState.ShowingSheet || shareState is ShareState.Loading || shareState is ShareState.Error) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
            ) {
                Text(
                    text = "Share Conversation",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )

                if (shareState is ShareState.Loading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    ShareOptionRow(
                        icon = Icons.Default.Share,
                        title = "Share this message pair",
                        description = "Shares only the selected user message and assistant response.",
                        onClick = onShareTurn,
                    )

                    ShareOptionRow(
                        icon = Icons.Default.Chat,
                        title = "Share full conversation",
                        description = "Shares the entire chat history.",
                        onClick = onShareFullChat,
                    )

                    if (shareState is ShareState.Error) {
                        Text(
                            text = shareState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
