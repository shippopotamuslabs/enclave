package com.browntowndev.pocketcrew.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.feature.chat.components.InputBar
import com.browntowndev.pocketcrew.feature.chat.components.MessageList
import com.browntowndev.pocketcrew.feature.chat.components.ShieldOverlay
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onNavigateToHistory: () -> Unit,
    onNewChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onModeChange: (ChatModeUi) -> Unit,
    onInputChange: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onImageSelected: (String?) -> Unit,
    onClearImage: () -> Unit,
    onShieldTap: () -> Unit,
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onImageSelected(uri?.toString())
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                onMenuClick = onNavigateToHistory,
                onNewChatClick = onNewChat,
                isThinking = uiState.isGenerating,
            )
        },
        contentWindowInsets = WindowInsets(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Message area — shrinks via weight(1f) when keyboard opens
            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    modifier = Modifier.fillMaxSize(),
                    messages = uiState.messages,
                    hasActiveIndicator = uiState.hasActiveIndicator,
                    activeToolCallBanner = uiState.activeToolCallBanner,
                    activeIndicatorMessageId = uiState.activeIndicatorMessageId,
                    onEditMessage = onEditMessage,
                )

                if (uiState.shieldReason != null) {
                    ShieldOverlay(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        reason = uiState.shieldReason ?: "",
                        onTap = onShieldTap,
                    )
                }
            }

            // InputBar — pinned above keyboard
            InputBar(
                modifier = Modifier.fillMaxWidth(),
                inputText = uiState.inputText,
                selectedImageUri = uiState.selectedImageUri,
                isPhotoAttachmentEnabled = uiState.isPhotoAttachmentEnabled,
                photoAttachmentDisabledReason = uiState.photoAttachmentDisabledReason,
                selectedMode = uiState.selectedMode,
                isGenerating = uiState.isGenerating,
                isGlobalInferenceBlocked = uiState.isGlobalInferenceBlocked,
                onInputChange = onInputChange,
                onModeChange = onModeChange,
                onSend = onSendMessage,
                onStopGenerating = onStopGenerating,
                onAttach = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearAttachment = onClearImage,
            )
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewChatScreenLight() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onStopGenerating = {},
            onModeChange = {},
            onInputChange = {},
            onEditMessage = {},
            onImageSelected = {},
            onClearImage = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenDark() {
    PocketCrewTheme(darkTheme = true) {
        PreviewChatScreenLight()
    }
}

@Preview
@Composable
private fun PreviewChatScreenDynamic() {
    PocketCrewTheme(dynamicColor = true) {
        PreviewChatScreenLight()
    }
}

@Preview
@Composable
private fun PreviewChatScreenWithMessages() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                messages = fakeLongMessages,
                inputText = "Hello, how are you?",
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onStopGenerating = {},
            onModeChange = {},
            onInputChange = {},
            onEditMessage = {},
            onImageSelected = {},
            onClearImage = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenThinking() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                messages = fakeLongMessages.takeLast(1),
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onStopGenerating = {},
            onModeChange = {},
            onInputChange = {},
            onEditMessage = {},
            onImageSelected = {},
            onClearImage = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenShield() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                shieldReason = "Potential harm detected",
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onStopGenerating = {},
            onModeChange = {},
            onInputChange = {},
            onEditMessage = {},
            onImageSelected = {},
            onClearImage = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenExpandedInput() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                inputText = "This is a long input text to test expanded state...",
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onStopGenerating = {},
            onModeChange = {},
            onInputChange = {},
            onEditMessage = {},
            onImageSelected = {},
            onClearImage = {},
            onShieldTap = {},
        )
    }
}
