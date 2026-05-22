package com.browntowndev.enclave.feature.chat

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
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.components.InputBar
import com.browntowndev.enclave.feature.chat.components.ShareBottomSheet
import com.browntowndev.enclave.feature.chat.components.detailbottomsheet.ProvideDetailBottomSheet
import com.browntowndev.enclave.feature.chat.components.messagelist.MessageList
import com.browntowndev.enclave.feature.chat.components.ShieldOverlay
import com.browntowndev.enclave.feature.chat.components.StreamingResponseHapticEffect

@Suppress("LongParameterList")
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
    onImagesSelected: (List<String>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onFileSelected: (String?) -> Unit,
    onClearFile: () -> Unit,
    onShieldTap: () -> Unit,
    onMicClick: () -> Unit,
    onArtifactCardClick: (
        com.browntowndev.enclave.domain.model.chat.MessageId,
        com.browntowndev.enclave.feature.chat.ArtifactCardUiModel,
    ) -> Unit = { _, _ -> },
    onNavigateToStudio: (
        ids: List<String>,
        uris: List<String>,
        types: List<String>,
        prompts: List<String>,
    ) -> Unit = { _, _, _, _ -> },
    onShareClick: (MessageId) -> Unit = {},
    onShareTurn: () -> Unit = {},
    onShareFullChat: () -> Unit = {},
    onDismissShareSheet: () -> Unit = {},
) {
    StreamingResponseHapticEffect(
        hapticsEnabled = uiState.hapticResponse,
        phase = uiState.responseHapticPhase,
        pulseKey = uiState.responseHapticPulseKey,
    )

    val singleImagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            onImagesSelected(listOfNotNull(uri?.toString()))
        }

    val multipleImagePickerLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickMultipleVisualMedia(
                    maxItems = uiState.photoAttachmentMaxItems.coerceAtLeast(2),
                ),
        ) { uris ->
            onImagesSelected(uris.map { it.toString() })
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            onFileSelected(uri?.toString())
        }

    ProvideDetailBottomSheet {
        Scaffold(
            topBar = {
                ChatTopBar(
                    onMenuClick = onNavigateToHistory,
                    onNewChatClick = onNewChat,
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            ShareBottomSheet(
                shareState = uiState.shareState,
                onShareTurn = onShareTurn,
                onShareFullChat = onShareFullChat,
                onDismiss = onDismissShareSheet,
            )

            Column(
            modifier =
                Modifier
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
                    chatMode = uiState.selectedMode,
                    onEditMessage = onEditMessage,
                    onPlayAudio = onPlayAudio,
                    onArtifactCardClick = onArtifactCardClick,
                    onNavigateToStudio = onNavigateToStudio,
                    onShareClick = onShareClick,
                )

                if (uiState.shieldReason != null) {
                    ShieldOverlay(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                        reason = uiState.shieldReason ?: "",
                        onTap = onShieldTap,
                    )
                }
            }

            if (!uiState.isReadOnly) {
                InputBar(
                    modifier = Modifier.fillMaxWidth(),
                    inputText = uiState.inputText,
                    speechState = uiState.speechState,
                    selectedImageUris = uiState.selectedImageUris,
                    selectedFileName = uiState.selectedFileName,
                    isPhotoAttachmentEnabled = uiState.isPhotoAttachmentEnabled,
                    photoAttachmentMaxItems = uiState.photoAttachmentMaxItems,
                    photoAttachmentDisabledReason = uiState.photoAttachmentDisabledReason,
                    selectedMode = uiState.selectedMode,
                    isGenerating = uiState.isGenerating,
                    canStop = uiState.canStop,
                    isGlobalInferenceBlocked = uiState.isGlobalInferenceBlocked,
                    onInputChange = onInputChange,
                    onModeChange = onModeChange,
                    onSend = onSendMessage,
                    onStopGenerating = onStopGenerating,
                    onImageAttach = {
                        if (uiState.photoAttachmentMaxItems > 1) {
                            multipleImagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        } else {
                            singleImagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                    onFileAttach = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/json",
                                "application/xml",
                                "application/javascript",
                                "application/x-yaml",
                                "application/toml",
                            ),
                        )
                    },
                    onClearImage = onRemoveImage,
                    onClearFile = onClearFile,
                    onMicClick = onMicClick,
                )
            }
        }
        }
    }
}

