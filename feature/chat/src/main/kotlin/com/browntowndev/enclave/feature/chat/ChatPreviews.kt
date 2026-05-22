package com.browntowndev.enclave.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.feature.chat.components.InputBar

@Preview(showBackground = true, name = "Chat top bar — dark")
@Composable
fun PreviewChatTopBarDark() {
    EnclaveTheme(darkTheme = true) {
        ChatTopBar(onMenuClick = {}, onNewChatClick = {})
    }
}

@Preview(showBackground = true, name = "Chat screen — dark")
@Composable
fun PreviewChatScreenDark() {
    EnclaveTheme(darkTheme = true) {
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
            onImagesSelected = {},
            onRemoveImage = {},
            onFileSelected = {},
            onClearFile = {},
            onShieldTap = {},
            onMicClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Chat input bar — dark")
@Composable
fun PreviewChatInputBarDark() {
    EnclaveTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            InputBar(
                modifier = Modifier.fillMaxWidth(),
                inputText = "Hello World",
                speechState = SpeechState.Idle,
                selectedImageUris = emptyList(),
                selectedFileName = "test_file.txt",
                isPhotoAttachmentEnabled = true,
                photoAttachmentMaxItems = 1,
                photoAttachmentDisabledReason = null,
                selectedMode = ChatModeUi.FAST,
                isGenerating = false,
                canStop = true,
                isGlobalInferenceBlocked = false,
                onInputChange = {},
                onModeChange = {},
                onSend = {},
                onStopGenerating = {},
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
                onMicClick = {},
            )
        }
    }
}
