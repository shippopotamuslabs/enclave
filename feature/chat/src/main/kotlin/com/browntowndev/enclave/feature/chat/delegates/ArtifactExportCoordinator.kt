package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.artifact.ArtifactExportDestination
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportFormat
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.enclave.domain.model.artifact.ExportArtifactDocumentsCommand
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.enclave.feature.chat.ArtifactCardUiModel
import com.browntowndev.enclave.feature.chat.ArtifactExportSheetState
import com.browntowndev.enclave.feature.chat.ArtifactExportWizardStep
import com.browntowndev.enclave.feature.chat.WizardExportDestination
import com.browntowndev.enclave.feature.chat.WizardExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ArtifactExportCoordinator
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        private val loggingPort: LoggingPort,
    ) {
        private val _artifactExportSheet = MutableStateFlow<ArtifactExportSheetState>(ArtifactExportSheetState.Hidden)
        val artifactExportSheet: StateFlow<ArtifactExportSheetState> = _artifactExportSheet.asStateFlow()

        private val _artifactExportMessages = Channel<String>(Channel.BUFFERED)
        val artifactExportMessages: Flow<String> = _artifactExportMessages.receiveAsFlow()

        fun onArtifactCardClick(
            messageId: MessageId,
            card: ArtifactCardUiModel,
        ) {
            _artifactExportSheet.value =
                ArtifactExportSheetState.Visible(
                    messageId = messageId,
                    artifactId = card.artifactId,
                    title = card.title,
                    step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                    selectedFormat = null,
                    selectedDestination = null,
                    isExporting = false,
                    documentJson = card.documentJson,
                )
        }

        fun dismissArtifactExportSheet() {
            _artifactExportSheet.value = ArtifactExportSheetState.Hidden
        }

        fun onArtifactExportSelectFormat(format: WizardExportFormat) {
            val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
            _artifactExportSheet.value = visible.copy(selectedFormat = format, errorMessage = null)
        }

        fun onArtifactExportSelectDestination(destination: WizardExportDestination) {
            val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
            _artifactExportSheet.value = visible.copy(selectedDestination = destination, errorMessage = null)
        }

        fun onArtifactExportWizardBack() {
            when (val sheet = _artifactExportSheet.value) {
                is ArtifactExportSheetState.Visible -> {
                    when (sheet.step) {
                        ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
                            _artifactExportSheet.value =
                                sheet.copy(
                                    step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                                    selectedDestination = null,
                                    errorMessage = null,
                                )
                        }
                        ArtifactExportWizardStep.CHOOSE_FORMAT -> dismissArtifactExportSheet()
                    }
                }
                else -> Unit
            }
        }

        fun onArtifactExportWizardNext() {
            val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
            if (visible.selectedFormat == null) return
            _artifactExportSheet.value =
                visible.copy(step = ArtifactExportWizardStep.CHOOSE_DESTINATION, errorMessage = null)
        }

        fun onConfirmArtifactExport(
            scope: CoroutineScope,
            currentChatId: ChatId?,
            initialChatId: ChatId?,
        ) {
            val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
            if (visible.selectedFormat == null || visible.selectedDestination == null) return
            scope.launch {
                _artifactExportSheet.update { prev ->
                    (prev as? ArtifactExportSheetState.Visible)?.copy(
                        isExporting = true,
                        errorMessage = null,
                    ) ?: prev
                }
                try {
                    val documentJson =
                        resolveExportDocumentJson(visible, currentChatId, initialChatId)
                            ?: run {
                                showArtifactExportError("Chat is not ready. Try again in a moment.")
                                return@launch
                            }
                    if (documentJson.isBlank()) {
                        showArtifactExportError("Document is still generating. Try again in a moment.")
                        return@launch
                    }
                    val format =
                        when (visible.selectedFormat) {
                            WizardExportFormat.PDF -> ArtifactExportFormat.PDF
                            WizardExportFormat.WORD -> ArtifactExportFormat.WORD
                            WizardExportFormat.BOTH -> ArtifactExportFormat.BOTH
                        }
                    val destination =
                        when (visible.selectedDestination) {
                            WizardExportDestination.SHARE -> ArtifactExportDestination.SHARE
                            WizardExportDestination.DOWNLOAD -> ArtifactExportDestination.DOWNLOAD
                        }
                    val result =
                        exportArtifactDocumentsUseCase(
                            ExportArtifactDocumentsCommand(
                                documentJson = documentJson,
                                baseTitle = visible.title.ifBlank { "artifact" },
                                format = format,
                                destination = destination,
                            ),
                        )
                    result
                        .onSuccess { outcome ->
                            emitOutcomeMessage(outcome)
                            persistFormatPreferenceIfNeeded(visible.artifactId, format)
                            dismissArtifactExportSheet()
                        }.onFailure { error ->
                            loggingPort.error(TAG, "Artifact export failed", error)
                            showArtifactExportError(error.message ?: "Export failed")
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    loggingPort.error(TAG, "Artifact export failed", e)
                    showArtifactExportError(e.message ?: "Export failed")
                }
            }
        }

        suspend fun persistArtifactExportPreference(
            artifactId: String,
            format: MimeType,
        ) {
            withContext(Dispatchers.IO) {
                chatRepository.updateArtifactExportPreference(artifactId = artifactId, format = format)
            }
        }

        private suspend fun resolveExportDocumentJson(
            visible: ArtifactExportSheetState.Visible,
            currentChatId: ChatId?,
            initialChatId: ChatId?,
        ): String? {
            if (visible.documentJson.isNotBlank()) {
                return visible.documentJson
            }
            val chatId = currentChatId ?: initialChatId ?: return null
            return runCatching {
                chatRepository
                    .getMessagesForChat(chatId)
                    .first()
                    .firstOrNull { message -> message.id == visible.messageId }
                    ?.content
                    ?.artifacts
                    ?.firstOrNull { artifact -> artifact.artifactId == visible.artifactId }
                    ?.content
                    .orEmpty()
            }.getOrDefault("")
        }

        private suspend fun showArtifactExportError(message: String) {
            _artifactExportMessages.send(message)
            _artifactExportSheet.update { prev ->
                (prev as? ArtifactExportSheetState.Visible)?.copy(
                    isExporting = false,
                    errorMessage = message,
                ) ?: prev
            }
        }

        private suspend fun emitOutcomeMessage(outcome: ArtifactExportOutcome) {
            when (outcome) {
                is ArtifactExportOutcome.Shared ->
                    _artifactExportMessages.send(
                        "Share sheet opened for ${outcome.fileCount} file(s).",
                    )
                is ArtifactExportOutcome.Downloaded ->
                    _artifactExportMessages.send(
                        "Saved to Downloads: ${outcome.savedDisplayNames.joinToString()}",
                    )
            }
        }

        private suspend fun persistFormatPreferenceIfNeeded(
            artifactId: String,
            format: ArtifactExportFormat,
        ) {
            when (format) {
                ArtifactExportFormat.PDF -> persistArtifactExportPreference(artifactId, MimeType.PDF)
                ArtifactExportFormat.WORD -> persistArtifactExportPreference(artifactId, MimeType.WORD)
                ArtifactExportFormat.BOTH -> Unit
            }
        }

        private companion object {
            private const val TAG = "ArtifactExportCoordinator"
        }
    }
