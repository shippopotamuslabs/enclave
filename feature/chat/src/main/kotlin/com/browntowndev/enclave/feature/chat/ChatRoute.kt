package com.browntowndev.enclave.feature.chat

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.feature.chat.artifactexport.ArtifactExportWizardContent
import com.browntowndev.enclave.feature.chat.stats.ChatStatsBottomSheet
import com.browntowndev.enclave.feature.chat.stats.ResponseStatsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(
    onNavigateToHistory: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    onNewChat: () -> Unit,
    onNavigateToStudio: (ids: List<String>, uris: List<String>, types: List<String>, prompts: List<String>) -> Unit,
    navigationChatId: ChatId? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chatIdFromNavigation = navigationChatId ?: viewModel.initialChatId

    LaunchedEffect(chatIdFromNavigation) {
        chatIdFromNavigation?.let(viewModel::onNavigationChatIdChanged)
    }

    LaunchedEffect(Unit) {
        viewModel.shareUiEvents.collect { event ->
            when (event) {
                is ShareUiEvent.OpenShareSheet -> {
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.shareUrl)
                        }
                    context.startActivity(Intent.createChooser(intent, "Share Chat"))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.artifactExportMessages.collect { message ->
            onShowSnackbar(message, null)
        }
    }

    val exportSheet = uiState.artifactExportSheet

    ResponseStatsBottomSheet(
        state = uiState.responseStatsSheet,
        onDismiss = viewModel::dismissResponseStatsSheet,
    )

    ChatStatsBottomSheet(
        state = uiState.chatStatsSheet,
        onDismiss = viewModel::dismissChatStatsSheet,
    )

    if (exportSheet is ArtifactExportSheetState.Visible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        NeoModalBottomSheet(
            onDismissRequest = viewModel::dismissArtifactExportSheet,
            sheetState = sheetState,
        ) {
            ArtifactExportWizardContent(
                state = exportSheet,
                onDismiss = viewModel::dismissArtifactExportSheet,
                onSelectFormat = viewModel::onArtifactExportSelectFormat,
                onSelectDestination = viewModel::onArtifactExportSelectDestination,
                onBack = viewModel::onArtifactExportWizardBack,
                onNext = viewModel::onArtifactExportWizardNext,
                onConfirmExport = viewModel::onConfirmArtifactExport,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChatScreen(
            uiState = uiState,
            onNavigateToHistory = onNavigateToHistory,
            onNewChat = {
                viewModel.createNewChat()
                onNewChat()
            },
            onSendMessage = { viewModel.onSendMessage() },
            onStopGenerating = viewModel::stopGeneration,
            onModeChange = viewModel::onModeChange,
            onInputChange = viewModel::onInputChange,
            onEditMessage = viewModel::onEditMessage,
            onPlayAudio = viewModel::onPlayTts,
            onPauseAudio = viewModel::onPauseTts,
            onImagesSelected = viewModel::onImagesSelected,
            onRemoveImage = viewModel::removeImage,
            onFileSelected = viewModel::onFileSelected,
            onClearFile = viewModel::clearSelectedFile,
            onShieldTap = {
                if (uiState.isGlobalInferenceBlocked) {
                    onShowSnackbar("Procedural harm blocked – Inference is currently blocked", null)
                }
            },
            onMicClick = viewModel::onMicClick,
            onArtifactCardClick = viewModel::onArtifactCardClick,
            onNavigateToStudio = onNavigateToStudio,
            onShareClick = viewModel::onShareClick,
            onShareTurn = viewModel::onShareTurn,
            onShareFullChat = viewModel::onShareFullChat,
            onDismissShareSheet = viewModel::onDismissShareSheet,
            onStatsClick = viewModel::onResponseStatsClick,
            onViewChatStatsClick = viewModel::onChatStatsClick,
        )
    }
}
