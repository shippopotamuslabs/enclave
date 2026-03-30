package com.browntowndev.pocketcrew.feature.history

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.core.ui.R
import com.browntowndev.pocketcrew.core.ui.component.shimmerEffect
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

private sealed interface HistoryOptionsState {
    data object Hidden : HistoryOptionsState
    data class Sheet(val chat: HistoryChat) : HistoryOptionsState
    data class Dialog(val chat: HistoryChat) : HistoryOptionsState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
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
    var optionsState by remember { mutableStateOf<HistoryOptionsState>(HistoryOptionsState.Hidden) }
    val sheetState = rememberModalBottomSheetState()

    val colorScheme = MaterialTheme.colorScheme
    // Bolt Optimization: Group conditional remember into a single block with keys to ensure proper positional memoization
    val shimmerColors = remember(colorScheme, uiState.isLoading) {
        if (uiState.isLoading) {
            val base = colorScheme.onSurface.copy(alpha = 0.05f)
            val highlight = colorScheme.onSurface.copy(alpha = 0.15f)
            base to highlight
        } else null
    }

    val shimmerProgress = if (uiState.isLoading) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerProgress"
        )
    } else null

    Scaffold(
        topBar = {
            HistoryTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
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

            if (uiState.isLoading && shimmerProgress != null && shimmerColors != null) {
                items(5) {
                    HistoryChatSkeletonItem(
                        progressState = shimmerProgress,
                        baseColor = shimmerColors.first,
                        highlightColor = shimmerColors.second
                    )
                }
            } else {
                if (uiState.pinnedChats.isNotEmpty()) {
                    item {
                        SectionHeader(text = "Pinned")
                    }
                    items(
                        items = uiState.pinnedChats,
                        key = { it.id }
                    ) { chat ->
                        HistoryChatItem(
                            chat = chat,
                            onClick = { onChatClick(chat.id) },
                            onShowOptions = {
                                optionsState = HistoryOptionsState.Sheet(chat)
                            }
                        )
                    }
                }

                if (uiState.otherChats.isNotEmpty()) {
                    item {
                        SectionHeader(text = "Recent")
                    }
                    items(
                        items = uiState.otherChats,
                        key = { it.id }
                    ) { chat ->
                        HistoryChatItem(
                            chat = chat,
                            onClick = { onChatClick(chat.id) },
                            onShowOptions = {
                                optionsState = HistoryOptionsState.Sheet(chat)
                            }
                        )
                    }
                }
            }
        }
    }

    when (val state = optionsState) {
        is HistoryOptionsState.Hidden -> {}
        is HistoryOptionsState.Sheet -> {
            ModalBottomSheet(
                onDismissRequest = { 
                    if (optionsState is HistoryOptionsState.Sheet) {
                        optionsState = HistoryOptionsState.Hidden 
                    }
                },
                sheetState = sheetState,
                modifier = Modifier.testTag("ChatOptionsBottomSheet")
            ) {
                ChatOptionsContent(
                    isPinned = state.chat.isPinned,
                    onDelete = {
                        optionsState = HistoryOptionsState.Dialog(state.chat)
                    },
                    onRename = {
                        onRenameChat(state.chat.id, "Renamed Chat")
                        optionsState = HistoryOptionsState.Hidden
                    },
                    onUnpin = {
                        onUnpinChat(state.chat.id)
                        optionsState = HistoryOptionsState.Hidden
                    },
                    onPin = {
                        onPinChat(state.chat.id)
                        optionsState = HistoryOptionsState.Hidden
                    }
                )
            }
        }
        is HistoryOptionsState.Dialog -> {
            DeleteConfirmationDialog(
                onConfirm = {
                    onDeleteChat(state.chat.id)
                    optionsState = HistoryOptionsState.Hidden
                },
                onDismiss = {
                    optionsState = HistoryOptionsState.Hidden
                },
                modifier = Modifier.testTag("DeleteConfirmationDialog")
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chat?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = "This chat will be permanently deleted. This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("ConfirmDeleteButton")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("CancelDeleteButton")
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
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
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.edit_square),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "New Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChatItem(
    chat: HistoryChat,
    onClick: () -> Unit,
    onShowOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShowOptions
            )
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
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
        
        IconButton(onClick = onShowOptions) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryChatSkeletonItem(
    progressState: State<Float>,
    baseColor: Color,
    highlightColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect(
                        progressState = progressState,
                        baseColor = baseColor,
                        highlightColor = highlightColor
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect(
                        progressState = progressState,
                        baseColor = baseColor,
                        highlightColor = highlightColor
                    )
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .shimmerEffect(
                        progressState = progressState,
                        baseColor = baseColor,
                        highlightColor = highlightColor
                    )
            )
        }
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
            onClick = onDelete,
            modifier = Modifier.testTag("DeleteOption")
        )
        BottomSheetOption(
            icon = Icons.Default.Edit,
            text = "Rename",
            onClick = onRename
        )
        if (isPinned) {
            BottomSheetOption(
                icon = Icons.Outlined.PushPin,
                text = "Unpin",
                onClick = onUnpin
            )
        } else {
            BottomSheetOption(
                icon = Icons.Default.PushPin,
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
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomSheetOption(text, contentColor, onClick, modifier) {
        Icon(painter = painter, contentDescription = null)
    }
}

@Composable
private fun BottomSheetOption(
    icon: ImageVector,
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomSheetOption(text, contentColor, onClick, modifier) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryScreenLoading() {
    PocketCrewTheme {
        HistoryScreen(
            uiState = HistoryUiState(isLoading = true),
            searchQuery = "",
            onSearchQueryChange = {},
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

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryScreenLight() {
    PocketCrewTheme {
        HistoryScreen(
            uiState = HistoryUiState(
                pinnedChats = listOf(HistoryChat(1, "Important Project", "10:30 AM", true)),
                otherChats = listOf(
                    HistoryChat(2, "Weekend Plans", "Yesterday", false),
                    HistoryChat(3, "Recipe Ideas", "2 days ago", false)
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
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

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryScreenDark() {
    PocketCrewTheme(darkTheme = true) {
        HistoryScreen(
            uiState = HistoryUiState(
                pinnedChats = listOf(HistoryChat(1, "Important Project", "10:30 AM", true)),
                otherChats = listOf(
                    HistoryChat(2, "Weekend Plans", "Yesterday", false),
                    HistoryChat(3, "Recipe Ideas", "2 days ago", false)
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
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

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryScreenEmpty() {
    PocketCrewTheme {
        HistoryScreen(
            uiState = HistoryUiState(
                pinnedChats = emptyList(),
                otherChats = emptyList()
            ),
            searchQuery = "",
            onSearchQueryChange = {},
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
