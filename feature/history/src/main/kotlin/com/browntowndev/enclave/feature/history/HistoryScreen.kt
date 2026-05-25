package com.browntowndev.enclave.feature.history

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoAppBarChrome
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.NeoNavRow
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSettingsSection
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.shimmerEffect
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.chat.ChatId

private sealed interface HistoryOptionsState {
    data object Hidden : HistoryOptionsState

    data class Sheet(
        val chat: HistoryChat,
    ) : HistoryOptionsState

    data class Dialog(
        val chat: HistoryChat,
    ) : HistoryOptionsState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onChatClick: (ChatId) -> Unit,
    onNewChatClick: () -> Unit,
    onStudioClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDeleteChat: (ChatId) -> Unit,
    onRenameChat: (ChatId, String) -> Unit,
    onPinChat: (ChatId) -> Unit,
    onUnpinChat: (ChatId) -> Unit,
    onSettingsClick: () -> Unit,
) {
    var optionsState by remember { mutableStateOf<HistoryOptionsState>(HistoryOptionsState.Hidden) }
    val sheetState = rememberModalBottomSheetState()

    // Always compute shimmer — used for loading skeleton AND inline syncing items
    val colorScheme = MaterialTheme.colorScheme
    val shimmerBaseColor = colorScheme.onSurface.copy(alpha = 0.05f)
    val shimmerHighlightColor = colorScheme.onSurface.copy(alpha = 0.15f)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerProgress",
        )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NeoAppBarChrome {
                HistoryTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onBackClick = onBackClick,
                    onSettingsClick = onSettingsClick,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            item {
                NeoSettingsSection(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                            .padding(top = 8.dp, bottom = EnclaveNeoTokens.sectionSpacing),
                ) {
                    NeoNavRow(
                        title = "New Chat",
                        icon = Icons.Default.Edit,
                        onClick = onNewChatClick,
                    )
                    NeoNavRow(
                        title = "Studio",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onStudioClick,
                    )
                    NeoNavRow(
                        title = "Gallery",
                        icon = Icons.Default.PhotoLibrary,
                        onClick = onGalleryClick,
                    )
                }
            }

            if (uiState.isLoading) {
                items(5) {
                    HistoryChatSkeletonItem(
                        progressState = shimmerProgress,
                        baseColor = shimmerBaseColor,
                        highlightColor = shimmerHighlightColor,
                        modifier = historyListItemModifier(),
                    )
                }
            } else {
                if (uiState.pinnedChats.isNotEmpty()) {
                    item {
                        NeoSectionHeader(
                            text = "Pinned",
                            modifier = Modifier.padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
                        )
                    }
                    items(
                        items = uiState.pinnedChats,
                        key = { it.id.value },
                    ) { chat ->
                        if (chat.isSyncing) {
                            HistoryChatSkeletonItem(
                                progressState = shimmerProgress,
                                baseColor = shimmerBaseColor,
                                highlightColor = shimmerHighlightColor,
                                modifier = historyListItemModifier(),
                            )
                        } else {
                            HistoryChatItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id) },
                                onShowOptions = {
                                    optionsState = HistoryOptionsState.Sheet(chat)
                                },
                                modifier = historyListItemModifier(),
                            )
                        }
                    }
                }

                if (uiState.otherChats.isNotEmpty()) {
                    item {
                        NeoSectionHeader(
                            text = "Recent",
                            modifier = Modifier.padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
                        )
                    }
                    items(
                        items = uiState.otherChats,
                        key = { it.id.value },
                    ) { chat ->
                        if (chat.isSyncing) {
                            HistoryChatSkeletonItem(
                                progressState = shimmerProgress,
                                baseColor = shimmerBaseColor,
                                highlightColor = shimmerHighlightColor,
                                modifier = historyListItemModifier(),
                            )
                        } else {
                            HistoryChatItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id) },
                                onShowOptions = {
                                    optionsState = HistoryOptionsState.Sheet(chat)
                                },
                                modifier = historyListItemModifier(),
                            )
                        }
                    }
                }
            }
        }
    }

    when (val state = optionsState) {
        is HistoryOptionsState.Hidden -> {}
        is HistoryOptionsState.Sheet -> {
            NeoModalBottomSheet(
                onDismissRequest = {
                    if (optionsState is HistoryOptionsState.Sheet) {
                        optionsState = HistoryOptionsState.Hidden
                    }
                },
                sheetState = sheetState,
                modifier = Modifier.testTag("ChatOptionsBottomSheet"),
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
                    },
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
                modifier = Modifier.testTag("DeleteConfirmationDialog"),
            )
        }
    }
}

private fun historyListItemModifier(): Modifier =
    Modifier
        .fillMaxWidth()
        .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
        .padding(bottom = EnclaveNeoTokens.rowSpacing)

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chat?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(text = "This chat will be permanently deleted. This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                modifier = Modifier.testTag("ConfirmDeleteButton"),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = Modifier.testTag("CancelDeleteButton"),
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChatItem(
    chat: HistoryChat,
    onClick: () -> Unit,
    onShowOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = neoBorderColor()
    val backgroundColor = MaterialTheme.colorScheme.surface

    Row(
        modifier =
            modifier
                .neoSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = false,
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onShowOptions,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
        ) {
            Text(
                text = chat.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = chat.lastMessageDateTime,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NeoIconButton(
            onClick = onShowOptions,
            icon = Icons.Default.MoreVert,
            contentDescription = "Options",
        )
    }
}

@Composable
private fun HistoryChatSkeletonItem(
    progressState: State<Float>,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    val borderColor = neoBorderColor()
    val backgroundColor = MaterialTheme.colorScheme.surface

    Row(
        modifier =
            modifier
                .neoSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = false,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect(
                            progressState = progressState,
                            baseColor = baseColor,
                            highlightColor = highlightColor,
                        ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect(
                            progressState = progressState,
                            baseColor = baseColor,
                            highlightColor = highlightColor,
                        ),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier =
                Modifier
                    .size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .shimmerEffect(
                            progressState = progressState,
                            baseColor = baseColor,
                            highlightColor = highlightColor,
                        ),
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
    onPin: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
    ) {
        BottomSheetOption(
            icon = Icons.Default.Delete,
            text = "Delete",
            contentColor = MaterialTheme.colorScheme.error,
            onClick = onDelete,
            modifier = Modifier.testTag("DeleteOption"),
        )
        BottomSheetOption(
            icon = Icons.Default.Edit,
            text = "Rename",
            onClick = onRename,
        )
        if (isPinned) {
            BottomSheetOption(
                icon = Icons.Outlined.PushPin,
                text = "Unpin",
                onClick = onUnpin,
            )
        } else {
            BottomSheetOption(
                icon = Icons.Default.PushPin,
                text = "Pin",
                onClick = onPin,
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
    icon: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
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
    modifier: Modifier = Modifier,
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
    modifier: Modifier = Modifier,
) {
    BottomSheetOption(text, contentColor, onClick, modifier) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

