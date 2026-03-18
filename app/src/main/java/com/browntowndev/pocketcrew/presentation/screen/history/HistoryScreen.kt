package com.browntowndev.pocketcrew.presentation.screen.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.R
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onBackClick: () -> Unit,
    onChatClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onRenameChat: (Long, String) -> Unit,
    onPinChat: (Long) -> Unit,
    onUnpinChat: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onShowSnackbar: (String, String?) -> Unit
) {
    var selectedChatForOptions by remember { mutableStateOf<HistoryChat?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HistoryTopBar(
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                NewChatButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    onClick = onNewChatClick
                )
            }

            if (uiState.pinnedChats.isNotEmpty()) {
                item {
                    SectionHeader(text = "Pinned")
                }
                items(uiState.pinnedChats) { chat ->
                    HistoryChatItem(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onLongClick = {
                            selectedChatForOptions = chat
                            showBottomSheet = true
                        }
                    )
                }
            }

            if (uiState.otherChats.isNotEmpty()) {
                item {
                    SectionHeader(text = "Recent")
                }
                items(uiState.otherChats) { chat ->
                    HistoryChatItem(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onLongClick = {
                            selectedChatForOptions = chat
                            showBottomSheet = true
                        }
                    )
                }
            }
        }
    }

    if (showBottomSheet && selectedChatForOptions != null) {
        val chat = selectedChatForOptions!!
        ModalBottomSheet(
            onDismissRequest = { 
                showBottomSheet = false
                selectedChatForOptions = null
            },
            sheetState = sheetState
        ) {
            ChatOptionsContent(
                isPinned = chat.isPinned,
                onDelete = {
                    onDeleteChat(chat.id)
                    showBottomSheet = false
                    selectedChatForOptions = null
                },
                onRename = {
                    onRenameChat(chat.id, "Renamed Chat")
                    showBottomSheet = false
                    selectedChatForOptions = null
                },
                onUnpin = {
                    onUnpinChat(chat.id)
                    showBottomSheet = false
                    selectedChatForOptions = null
                },
                onPin = {
                    onPinChat(chat.id)
                    showBottomSheet = false
                    selectedChatForOptions = null
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun NewChatButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.edit_square),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "New Chat",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChatItem(
    chat: HistoryChat,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = chat.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = chat.lastMessageDateTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatOptionsContent(
    isPinned: Boolean,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onUnpin: () -> Unit,
    onPin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        BottomSheetOption(
            icon = Icons.Default.Delete,
            text = "Delete",
            contentColor = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
        BottomSheetOption(
            icon = Icons.Default.Edit,
            text = "Rename",
            onClick = onRename
        )
        if (isPinned) {
            BottomSheetOption(
                painter = painterResource(R.drawable.pin),
                text = "Unpin",
                onClick = onUnpin
            )
        } else {
            BottomSheetOption(
                painter = painterResource(R.drawable.unpin),
                text = "Pin",
                onClick = onPin
            )
        }
    }
}

@Composable
private fun BottomSheetOption(
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BottomSheetOption(
    painter: Painter,
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    BottomSheetOption(text, contentColor, onClick) {
        Icon(painter = painter, contentDescription = null)
    }
}

@Composable
private fun BottomSheetOption(
    icon: ImageVector,
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    BottomSheetOption(text, contentColor, onClick) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryScreen() {
    PocketCrewTheme {
        HistoryScreen(
            uiState = HistoryUiState(
                pinnedChats = listOf(HistoryChat(1, "Pinned Chat", "10:30 AM", true)),
                otherChats = listOf(HistoryChat(2, "Recent Chat", "Yesterday", false))
            ),
            onBackClick = {},
            onChatClick = {},
            onNewChatClick = {},
            onDeleteChat = {},
            onRenameChat = { _, _ -> },
            onPinChat = {},
            onUnpinChat = {},
            onSettingsClick = {},
            onShowSnackbar = { _, _ -> }
        )
    }
}
