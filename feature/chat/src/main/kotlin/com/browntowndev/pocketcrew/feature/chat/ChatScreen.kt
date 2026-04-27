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
    onPlayAudio: (String) -> Unit,
    onImageSelected: (String?) -> Unit,
    onClearImage: () -> Unit,
    onShieldTap: () -> Unit,
    onMicClick: () -> Unit,
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
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
                    hasTtsProviderAssigned = uiState.hasTtsProviderAssigned,
                    onEditMessage = onEditMessage,
                    onPlayAudio = onPlayAudio,
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

            InputBar(
                modifier = Modifier.fillMaxWidth(),
                inputText = uiState.inputText,
                speechState = uiState.speechState,
                selectedImageUri = uiState.selectedImageUri,
                isPhotoAttachmentEnabled = uiState.isPhotoAttachmentEnabled,
                photoAttachmentDisabledReason = uiState.photoAttachmentDisabledReason,
                selectedMode = uiState.selectedMode,
                isGenerating = uiState.isGenerating,
                canStop = uiState.canStop,
                isGlobalInferenceBlocked = uiState.isGlobalInferenceBlocked,
                onInputChange = onInputChange,
                onModeChange = onModeChange,
                onSend = onSendMessage,
                onStopGenerating = onStopGenerating,
                onAttach = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClearAttachment = onClearImage,
                onMicClick = onMicClick,
            )
        }
    }
}

@Preview(showBackground = true)
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
            onPlayAudio = {},
            onImageSelected = {},
            onClearImage = {},
            onShieldTap = {},
            onMicClick = {},
        )
    }
}
