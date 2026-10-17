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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.core.ui.component.assignment.AssignmentSelectionBottomSheet
import com.browntowndev.enclave.feature.chat.components.InputBar
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import com.browntowndev.enclave.feature.chat.components.LiveVoiceOverlay
import com.browntowndev.enclave.feature.chat.components.ShareBottomSheet
import com.browntowndev.enclave.feature.chat.components.ShieldOverlay
import com.browntowndev.enclave.feature.chat.components.StreamingResponseHapticEffect
import com.browntowndev.enclave.feature.chat.components.detailbottomsheet.ProvideDetailBottomSheet
import com.browntowndev.enclave.feature.chat.components.messagelist.MessageList

@Suppress("LongParameterList")
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onNavigateToHistory: () -> Unit,
    onNewChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onModeChange: (ChatModeUi) -> Unit,
    onOtherClick: () -> Unit = {},
    onDismissModelOverrideSheet: () -> Unit = {},
    onModelOverrideSelected: (
        localId: com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId?,
        apiId: com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId?,
        ttsId: com.browntowndev.enclave.domain.model.config.TtsProviderId?,
        mediaId: com.browntowndev.enclave.domain.model.config.MediaProviderId?,
        liveVoiceId: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId?,
    ) -> Unit = { _, _, _, _, _ -> },
    onStartLiveVoice: () -> Unit = {},
    onEndLiveVoice: () -> Unit = {},
    onToggleLiveVoiceMute: () -> Unit = {},
    onInputChange: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    onPauseAudio: () -> Unit,
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
    onStatsClick: (MessageId) -> Unit = {},
    onViewChatStatsClick: () -> Unit = {},
    onReportResponse: (MessageId) -> Unit = {},
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
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ChatTopBar(
                        selectedMode = uiState.selectedMode,
                        selectedModeModelName = uiState.selectedModeModelName,
                        modeModelNames = uiState.modeModelNames,
                        onModeChange = onModeChange,
                        onOtherClick = onOtherClick,
                        onMenuClick = onNavigateToHistory,
                        onNewChatClick = onNewChat,
                        onViewChatStatsClick = onViewChatStatsClick,
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

                if (uiState.isModelOverrideSheetVisible) {
                    AssignmentSelectionBottomSheet(
                        slotLabel = "Other",
                        localAssets = uiState.modelOverrideSheetLocalAssets,
                        apiAssets = uiState.modelOverrideSheetApiAssets,
                        ttsAssets = emptyList(),
                        liveVoiceAssets = emptyList(),
                        mediaAssets = emptyList(),
                        onDismiss = onDismissModelOverrideSheet,
                        onSelect = onModelOverrideSelected,
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding()),
                ) {
                    // Message area — shrinks via weight(1f) when keyboard opens
                    Box(modifier = Modifier.weight(1f)) {
                        MessageList(
                            modifier = Modifier.fillMaxSize(),
                            messages = uiState.messages,
                            hasActiveIndicator = uiState.hasActiveIndicator,
                            activeToolCallBanner = uiState.activeToolCallBanner,
                            activeIndicatorMessageId = uiState.activeIndicatorMessageId?.value,
                            hasTtsProviderAssigned = uiState.hasTtsProviderAssigned,
                            isPlayingTts = uiState.isPlayingTts,
                            isLoadingTts = uiState.isLoadingTts,
                            isPausedTts = uiState.isPausedTts,
                            chatMode = uiState.selectedMode,
                            onEditMessage = onEditMessage,
                            onPlayAudio = onPlayAudio,
                            onPauseAudio = onPauseAudio,
                            onArtifactCardClick = onArtifactCardClick,
                            onNavigateToStudio = onNavigateToStudio,
                            onShareClick = onShareClick,
                            onStatsClick = onStatsClick,
                            onReportResponse = onReportResponse,
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
                            speechState = uiState.speechState.toChatSpeechStateUi(),
                            selectedImageUris = uiState.selectedImageUris,
                            selectedFileName = uiState.selectedFileName,
                            isPhotoAttachmentEnabled = uiState.isPhotoAttachmentEnabled,
                            photoAttachmentMaxItems = uiState.photoAttachmentMaxItems,
                            photoAttachmentDisabledReason = uiState.photoAttachmentDisabledReason,
                            isGenerating = uiState.isGenerating,
                            canStop = uiState.canStop,
                            isGlobalInferenceBlocked = uiState.isGlobalInferenceBlocked,
                            isLiveVoiceEnabled = uiState.hasLiveVoiceProviderAssigned,
                            onInputChange = onInputChange,
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
                            onLiveVoiceClick = onStartLiveVoice,
                        )
                    }
                }
            }

            if (uiState.liveVoiceState !is LiveVoiceUiState.Idle) {
                LiveVoiceOverlay(
                    modifier = Modifier.fillMaxSize(),
                    state = uiState.liveVoiceState,
                    onEnd = onEndLiveVoice,
                    onToggleMute = onToggleLiveVoiceMute,
                )
            }
        }
    }
}
