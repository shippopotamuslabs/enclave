package com.browntowndev.enclave.feature.chat
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportDestination
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportFormat
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.ExportArtifactDocumentsCommand
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import io.mockk.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModelTest {
    companion object {
        const val OSDA_V01_REQUIRED_MESSAGE_COUNT = 1
    }

    private val settingsUseCases: SettingsUseCases = mockk()
    private val chatUseCases: ChatUseCases = mockk()
    private val processFileAttachmentUseCase: ProcessFileAttachmentUseCase = mockk()
    private val stageImageAttachmentUseCase: StageImageAttachmentUseCase = mockk(relaxed = true)
    private val cancelInferenceUseCase: CancelInferenceUseCase = mockk(relaxed = true)
    private val inferenceLockManager: InferenceLockManager = mockk()
    private val toolExecutionEventPort: ToolExecutionEventPort = mockk()
    private var modelDisplayNamesUseCase: GetModelDisplayNameUseCase = mockk()
    private val errorHandler: ViewModelErrorHandler = mockk()
    private val loggingPort: LoggingPort = mockk(relaxed = true)
    private val activeChatTurnStore: ActiveChatTurnSnapshotPort = mockk(relaxed = true)
    private val playbackController: TtsPlaybackControllerPort = mockk(relaxed = true)
    private val studioRepository: StudioRepositoryPort = mockk(relaxed = true)
    private val shareChatUseCase: ShareChatUseCase = mockk(relaxed = true)
    private val chatRepository: com.browntowndev.enclave.domain.port.repository.ChatRepository = mockk(relaxed = true)
    private val exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase = mockk(relaxed = true)
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var defaultModelsFlow: MutableStateFlow<List<DefaultModelAssignment>>

    private lateinit var speechEvents: MutableSharedFlow<SpeechState>
    private lateinit var listenToSpeechUseCase: ListenToSpeechUseCase

    @BeforeEach
    fun setUp() {
        val activeModelProvider: ActiveModelProviderPort = mockk()
        val visionConfig =
            ActiveModelConfiguration(
                modelId = "test-model",
                id = ApiModelConfigurationId("vision-api-config"),
                isLocal = false,
                name = "Vision API",
                systemPrompt = "Describe images briefly.",
                reasoningEffort = null,
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                maxTokens = 512,
                minP = 0.0,
                repetitionPenalty = 1.1,
                contextWindow = 4096,
                thinkingEnabled = false,
                isMultimodal = true,
            )
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns visionConfig

        // Stub coroutineExceptionHandler to return a real one to avoid ClassCastException with MockK
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }
        every { errorHandler.handleError(any(), any(), any(), any()) } just Runs

        speechEvents = MutableSharedFlow(extraBufferCapacity = 8)
        listenToSpeechUseCase = mockk()
        every { listenToSpeechUseCase.invoke(any(), any()) } returns speechEvents
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase
        every { chatUseCases.processFileAttachmentUseCase } returns processFileAttachmentUseCase

        coEvery { chatUseCases.getChat(any()) } returns MutableStateFlow(emptyList())
        every { chatUseCases.mergeMessagesUseCase } returns MergeMessagesUseCase()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        defaultModelsFlow = MutableStateFlow(emptyList())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns defaultModelsFlow
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        // Create a simple fake for GetModelDisplayNameUseCase
        modelDisplayNamesUseCase = mockk(relaxed = true)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

        savedStateHandle = SavedStateHandle()

        chatViewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = savedStateHandle,
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                activeChatTurnSnapshotPort = activeChatTurnStore,
                playbackController = playbackController,
                studioRepository = studioRepository,
                shareChatUseCase = shareChatUseCase,
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            )
    }

    @Test
    fun `onSendMessage error invokes errorHandler`() =
        runTest {
            // Given
            val input = "Hello"
            chatViewModel.onInputChange(input)

            val exception = IOException("Network error")
            coEvery { chatUseCases.processPrompt(any()) } throws exception

            // When
            chatViewModel.onSendMessage()

            // Then
            // Verify coroutineExceptionHandler was called with correct parameters
            verify {
                errorHandler.handleError(
                    tag = "InferenceOrchestrator",
                    message = "Failed to send message",
                    throwable = exception,
                    userMessage = "Could not send message. Please try again.",
                )
            }
        }

    @Test
    fun `initial speech state is Idle`() =
        runTest {
            assertEquals(SpeechState.Idle, chatViewModel.uiState.value.speechState)
        }

    @Test
    fun `onConfirmArtifactExport uses selected card document json`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            val documentJson = """{"id":"artifact-1","metadata":{"title":"Doc"},"blocks":[]}"""
            coEvery { exportArtifactDocumentsUseCase(any()) } returns
                Result.success(ArtifactExportOutcome.Downloaded(listOf("Doc.pdf")))

            chatViewModel.onArtifactCardClick(
                messageId = MessageId("message-1"),
                card =
                    ArtifactCardUiModel(
                        artifactId = "artifact-1",
                        title = "Doc",
                        documentJson = documentJson,
                        badgeLabel = "Doc",
                        subtitle = "Document • Generated",
                        ctaLabel = "Export",
                    ),
            )
            chatViewModel.onArtifactExportSelectFormat(WizardExportFormat.PDF)
            chatViewModel.onArtifactExportWizardNext()
            chatViewModel.onArtifactExportSelectDestination(WizardExportDestination.DOWNLOAD)

            chatViewModel.artifactExportMessages.test {
                chatViewModel.onConfirmArtifactExport()
                runCurrent()

                assertEquals("Saved to Downloads: Doc.pdf", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            coVerify {
                exportArtifactDocumentsUseCase(
                    match<ExportArtifactDocumentsCommand> { command ->
                        command.documentJson == documentJson &&
                            command.baseTitle == "Doc" &&
                            command.format == ArtifactExportFormat.PDF &&
                            command.destination == ArtifactExportDestination.DOWNLOAD
                    },
                )
            }
            coVerify(exactly = 0) { chatRepository.getMessagesForChat(any()) }
            assertEquals(ArtifactExportSheetState.Hidden, chatViewModel.uiState.value.artifactExportSheet)
            collectJob.cancel()
        }

    @Test
    fun `onConfirmArtifactExport keeps sheet visible with error when document is blank`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            val chatId = ChatId("chat-blank-export")
            savedStateHandle["chatId"] = chatId.value
            every { chatRepository.getMessagesForChat(chatId) } returns flowOf(emptyList())
            chatViewModel.onArtifactCardClick(
                messageId = MessageId("message-1"),
                card =
                    ArtifactCardUiModel(
                        artifactId = "artifact-1",
                        title = "Doc",
                        documentJson = "",
                        badgeLabel = "Doc",
                        subtitle = "Document • Generated",
                        ctaLabel = "Export",
                    ),
            )
            chatViewModel.onArtifactExportSelectFormat(WizardExportFormat.WORD)
            chatViewModel.onArtifactExportWizardNext()
            chatViewModel.onArtifactExportSelectDestination(WizardExportDestination.SHARE)

            chatViewModel.artifactExportMessages.test {
                chatViewModel.onConfirmArtifactExport()
                runCurrent()

                assertEquals(
                    "Document is still generating. Try again in a moment.",
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { exportArtifactDocumentsUseCase(any()) }
            val sheet = chatViewModel.uiState.value.artifactExportSheet as ArtifactExportSheetState.Visible
            assertFalse(sheet.isExporting)
            assertEquals("Document is still generating. Try again in a moment.", sheet.errorMessage)
            collectJob.cancel()
        }

    @Test
    fun `FAAL-AR-05 onConfirmArtifactExport hydrates blank card from repository`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            val chatId = ChatId("chat-hydrate-1")
            savedStateHandle["chatId"] = chatId.value
            val hydratedJson =
                """{"version":"1.0","metadata":{"title":"Doc"},"blocks":[{"type":"paragraph","text":"Body"}]}"""
            coEvery { exportArtifactDocumentsUseCase(any()) } returns
                Result.success(ArtifactExportOutcome.Downloaded(listOf("Doc.pdf")))
            every { chatRepository.getMessagesForChat(chatId) } returns
                flowOf(
                    listOf(
                        Message(
                            id = MessageId("message-1"),
                            chatId = chatId,
                            role = Role.ASSISTANT,
                            content =
                                Content(
                                    text = "artifact",
                                    artifacts =
                                        listOf(
                                            ArtifactGenerationRequest(
                                                artifactId = "doc-export-1",
                                                title = "Doc",
                                                content = hydratedJson,
                                            ),
                                        ),
                                ),
                        ),
                    ),
                )
            every { chatRepository.getMessagesForChat(not(chatId)) } returns flowOf(emptyList())

            chatViewModel.onArtifactCardClick(
                messageId = MessageId("message-1"),
                card =
                    ArtifactCardUiModel(
                        artifactId = "doc-export-1",
                        title = "Doc",
                        documentJson = "",
                        badgeLabel = "Doc",
                        subtitle = "Document • Generated",
                        ctaLabel = "Export",
                    ),
            )
            chatViewModel.onArtifactExportSelectFormat(WizardExportFormat.PDF)
            chatViewModel.onArtifactExportWizardNext()
            chatViewModel.onArtifactExportSelectDestination(WizardExportDestination.DOWNLOAD)

            chatViewModel.artifactExportMessages.test {
                chatViewModel.onConfirmArtifactExport()
                runCurrent()

                assertEquals("Saved to Downloads: Doc.pdf", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            coVerify {
                exportArtifactDocumentsUseCase(
                    match<ExportArtifactDocumentsCommand> { command ->
                        command.documentJson.contains("Body") &&
                            command.baseTitle == "Doc" &&
                            command.format == ArtifactExportFormat.PDF &&
                            command.destination == ArtifactExportDestination.DOWNLOAD
                    },
                )
            }
            coVerify(exactly = 1) { chatRepository.getMessagesForChat(chatId) }
            collectJob.cancel()
        }

    @Test
    fun `onConfirmArtifactExport shows error when chat id missing for hydration`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            savedStateHandle["chatId"] = null

            chatViewModel.onArtifactCardClick(
                messageId = MessageId("message-1"),
                card =
                    ArtifactCardUiModel(
                        artifactId = "doc-export-1",
                        title = "Doc",
                        documentJson = "",
                        badgeLabel = "Doc",
                        subtitle = "Document • Generated",
                        ctaLabel = "Export",
                    ),
            )
            chatViewModel.onArtifactExportSelectFormat(WizardExportFormat.PDF)
            chatViewModel.onArtifactExportWizardNext()
            chatViewModel.onArtifactExportSelectDestination(WizardExportDestination.DOWNLOAD)

            chatViewModel.artifactExportMessages.test {
                chatViewModel.onConfirmArtifactExport()
                runCurrent()

                assertEquals("Chat is not ready. Try again in a moment.", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { chatRepository.getMessagesForChat(any()) }
            coVerify(exactly = 0) { exportArtifactDocumentsUseCase(any()) }
            collectJob.cancel()
        }

    @Test
    fun `onMicClick starts listening when Idle`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            chatViewModel.onMicClick()
            coVerify(exactly = 1) { listenToSpeechUseCase.invoke(any(), any()) }
            collectJob.cancel()
        }

    @Test
    fun `onMicClick sets stop signal when Listening`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            chatViewModel.onMicClick()
            speechEvents.emit(SpeechState.Listening(0f))
            runCurrent()
            chatViewModel.onMicClick()
            runCurrent()
            // Verify that the stop signal was sent. The actual state change to Idle will happen
            // when the use case processes the signal and completes.
            coVerify { listenToSpeechUseCase.invoke(any(), match { it.value }) }
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts launches streaming use case`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            verify { playbackController.play(text) }
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts does not launch use case when TTS is unassigned`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            runCurrent()

            chatViewModel.onPlayTts("Hello")

            collectJob.cancel()
        }

    @Test
    fun `onPlayTts sets isPlayingTts to true when Playing status received`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flow {
                    emit(TtsPlaybackStatus.Playing)
                    delay(10_000) // Keep flow alive so isPlayingTts stays true
                }

            chatViewModel.onPlayTts(text)
            runCurrent()

            assertTrue(chatViewModel.uiState.value.isPlayingTts)
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts sets isPlayingTts to false when Completed status received`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            // After Completed, isPlayingTts should be false
            assertFalse(chatViewModel.uiState.value.isPlayingTts)
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts sets isPlayingTts to false when Error status received`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Error("Connection failed"),
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            // After Error, isPlayingTts should be false
            assertFalse(chatViewModel.uiState.value.isPlayingTts)
            collectJob.cancel()
        }

    @Test
    fun `onStopTts cancels streaming job and stops use case`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()

            chatViewModel.onStopTts()

            verify { playbackController.stop() }
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts cancels previous streaming job before starting new one`() =
        runTest {
            val text1 = "Hello"
            val text2 = "Goodbye"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text1) } returns
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )
            every { playbackController.play(text2) } returns
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text1)
            runCurrent()
            chatViewModel.onPlayTts(text2)
            runCurrent()

            // stop() should be called at least once (from the cancel-before-start)
            verify(atLeast = 1) { playbackController.stop() }
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts does not call batch use case - streaming only`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            // Verify the playback controller was called
            verify { playbackController.play(text) }
            collectJob.cancel()
        }

    @Test
    fun `onStopTts stops streaming and cancels job`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flow {
                    emit(TtsPlaybackStatus.Playing)
                    delay(10_000) // Keep alive
                }

            chatViewModel.onPlayTts(text)
            runCurrent()
            assertTrue(chatViewModel.uiState.value.isPlayingTts)

            chatViewModel.onStopTts()
            runCurrent()

            verify { playbackController.stop() }
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts when no TTS provider assigned does nothing`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            // No TTS provider assigned - defaultModelsFlow is empty
            runCurrent()

            chatViewModel.onPlayTts("Hello")
            runCurrent()

            // playbackController.play should NOT be called
            verify(exactly = 0) { playbackController.play(any()) }
            assertFalse(chatViewModel.uiState.value.isPlayingTts)
            collectJob.cancel()
        }

    @Test
    fun `onPlayTts with Initializing status does not set isPlayingTts`() =
        runTest {
            val text = "Hello"
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()
            every { playbackController.play(text) } returns
                flowOf(
                    TtsPlaybackStatus.Initializing,
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            // Initializing should NOT set isPlayingTts to true
            assertFalse(chatViewModel.uiState.value.isPlayingTts)
            collectJob.cancel()
        }

    @Test
    fun `onFileSelected updates uiState with file name`() =
        runTest {
            val uri = "content://test.txt"
            val metadata = FileAttachmentMetadata("test.txt", "text/plain", "File content")
            coEvery { processFileAttachmentUseCase(uri) } returns metadata

            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }

            chatViewModel.onFileSelected(uri)
            advanceUntilIdle()

            assertEquals("test.txt", chatViewModel.uiState.value.selectedFileName)
            collectJob.cancel()
        }

    @Test
    fun `onSendMessage includes file content in prompt`() =
        runTest {
            val uri = "content://test.txt"
            val metadata = FileAttachmentMetadata("test.txt", "text/plain", "Extracted content")
            coEvery { processFileAttachmentUseCase(uri) } returns metadata

            // Mock prompt result
            val chatId = ChatId("chat")
            val assistantId = MessageId("assistant")
            coEvery { chatUseCases.processPrompt(any()) } returns
                UserPromptResult(
                    chatId = chatId,
                    userMessageId = MessageId("user"),
                    userMessageCreatedAtMillis = 100L,
                )
            coEvery {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            } returns
                com.browntowndev.enclave.domain.usecase.chat.AssistantPlaceholderResult(
                    assistantMessageId = assistantId,
                )
            coEvery { chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any()) } returns flowOf()

            chatViewModel.onFileSelected(uri)
            advanceUntilIdle()

            chatViewModel.onInputChange("Hello")
            chatViewModel.onSendMessage()
            advanceUntilIdle()

            coVerify {
                chatUseCases.processPrompt(
                    match {
                        it.content.text == "Hello" &&
                            it.content.fileAttachments.size == 1 &&
                            it.content.fileAttachments[0].name == "test.txt" &&
                            it.content.fileAttachments[0].content == "Extracted content"
                    },
                )
            }
        }

    @Test
    fun `UI state reconciles media URIs from StudioRepository`() =
        runTest {
            val chatId = ChatId("chat-1")
            val messageId = MessageId("msg-1")
            val assetId = "asset-1"
            val cacheUri = "file:///cache/test.jpg"
            val persistentUri = "file:///files/test.jpg"

            val studioMediaFlow = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
            every { studioRepository.observeAllMedia() } returns studioMediaFlow
            every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

            val messagesFlow =
                MutableStateFlow(
                    listOf(
                        Message(
                            id = messageId,
                            chatId = chatId,
                            content =
                                Content(
                                    text = "Here is an image",
                                    generatedMediaUris = listOf(cacheUri),
                                    generatedMediaAssetIds = listOf(assetId),
                                    generatedMediaTypes = listOf("IMAGE"),
                                    generatedMediaPrompts = listOf("A beautiful sunset"),
                                ),
                            role = Role.ASSISTANT,
                            messageState = MessageState.COMPLETE,
                            modelType = ModelType.FAST,
                        ),
                    ),
                )
            coEvery { chatUseCases.getChat(chatId) } returns messagesFlow

            val savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value))
            chatViewModel =
                ChatViewModelTestSupport.createViewModel(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = savedStateHandle,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = mockk(relaxed = true),
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    loggingPort = loggingPort,
                    activeChatTurnSnapshotPort = activeChatTurnStore,
                    playbackController = playbackController,
                    studioRepository = studioRepository,
                    shareChatUseCase = shareChatUseCase,
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )

            // Initial state should use cache URI
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            runCurrent()

            val initialMessage =
                chatViewModel.uiState.value.messages
                    .first()
            assertEquals(cacheUri, initialMessage.content.generatedMediaUris.first())

            // Simulate asset being saved to Gallery (URI change)
            studioMediaFlow.value =
                listOf(
                    StudioMediaAsset(
                        id = assetId,
                        localUri = persistentUri,
                        mediaType = "IMAGE",
                        prompt = "A beautiful sunset",
                        createdAt = 1L,
                    ),
                )
            runCurrent()

            // UI state should reflect reconciled persistent URI
            val updatedMessage =
                chatViewModel.uiState.value.messages
                    .first()
            assertEquals(persistentUri, updatedMessage.content.generatedMediaUris.first())

            collectJob.cancel()
        }

    @Test
    @DisplayName("OSDA-V-01 — uiState lists optimistic user before DB row")
    fun osdaV01_optimisticUserVisibleBeforeDb() =
        runTest {
            val dbFlow = MutableStateFlow<List<Message>>(emptyList())
            coEvery { chatUseCases.getChat(any()) } returns dbFlow

            val processPromptGate = CompletableDeferred<Unit>()
            coEvery { chatUseCases.processPrompt(any()) } coAnswers {
                processPromptGate.await()
                UserPromptResult(
                    userMessageId = MessageId("opt-1"),
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }

            val harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = savedStateHandle,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = mockk(relaxed = true),
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    activeChatTurnSnapshotPort = activeChatTurnStore,
                    playbackController = playbackController,
                    studioRepository = studioRepository,
                    shareChatUseCase = shareChatUseCase,
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )
            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect() }
            harness.viewModel.onInputChange("Pending")
            harness.viewModel.onSendMessage()
            runCurrent()

            val messages = harness.viewModel.uiState.value.messages
            assertEquals(1, messages.size)
            assertEquals(MessageRole.User, messages.last().role)
            assertEquals("Pending", messages.last().content.text)

            processPromptGate.complete(Unit)
            collectJob.cancel()
        }

    @Test
    @DisplayName("OSDA-V-02 — DB row replaces optimistic with same id")
    fun osdaV02_dbRowReplacesOptimistic() =
        runTest {
            val chatId = ChatId("chat-1")
            val dbFlow =
                MutableStateFlow(
                    listOf(
                        Message(
                            id = MessageId("opt-1"),
                            chatId = chatId,
                            content = Content(text = "Pending"),
                            role = Role.USER,
                        ),
                    ),
                )
            coEvery { chatUseCases.getChat(chatId) } returns dbFlow
            coEvery { chatUseCases.processPrompt(any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("opt-1"),
                    chatId = chatId,
                    userMessageCreatedAtMillis = 100L,
                )
            coEvery {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            } returns
                com.browntowndev.enclave.domain.usecase.chat.AssistantPlaceholderResult(
                    assistantMessageId = MessageId("asst-1"),
                )
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    mode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            val harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = mockk(relaxed = true),
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    activeChatTurnSnapshotPort = activeChatTurnStore,
                    playbackController = playbackController,
                    studioRepository = studioRepository,
                    shareChatUseCase = shareChatUseCase,
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )

            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect() }
            harness.viewModel.onInputChange("Pending")
            harness.viewModel.onSendMessage()
            advanceUntilIdle()

            assertEquals(1, harness.viewModel.uiState.value.messages.size)
            assertNull(harness.inferenceOrchestrator.optimisticUserMessage.value)
            verify(atLeast = 1) { harness.inferenceOrchestrator.clearOptimisticUser() }

            collectJob.cancel()
        }

    @Test
    @DisplayName("OSDA-V-03 — onSendMessage error invokes handler and no stuck optimistic")
    fun osdaV03_sendErrorClearsOptimistic() =
        runTest {
            val harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = savedStateHandle,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = mockk(relaxed = true),
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    activeChatTurnSnapshotPort = activeChatTurnStore,
                    playbackController = playbackController,
                    studioRepository = studioRepository,
                    shareChatUseCase = shareChatUseCase,
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )

            harness.viewModel.onInputChange("Hello")
            coEvery { chatUseCases.processPrompt(any()) } throws IOException("Network error")
            harness.viewModel.onSendMessage()
            advanceUntilIdle()

            verify {
                errorHandler.handleError(
                    tag = "InferenceOrchestrator",
                    message = "Failed to send message",
                    throwable = any(),
                    userMessage = "Could not send message. Please try again.",
                )
            }
            assertNull(harness.inferenceOrchestrator.optimisticUserMessage.value)
        }

    @Test
    @DisplayName("OSDA-M-02 — merge wired shows optimistic user when DB is empty")
    fun osdaM02_bypassMergeGuard() =
        runTest {
            val dbFlow = MutableStateFlow<List<Message>>(emptyList())
            coEvery { chatUseCases.getChat(any()) } returns dbFlow

            val processPromptGate = CompletableDeferred<Unit>()
            coEvery { chatUseCases.processPrompt(any()) } coAnswers {
                processPromptGate.await()
                UserPromptResult(
                    userMessageId = MessageId("opt-1"),
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }

            val harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = savedStateHandle,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = mockk(relaxed = true),
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    activeChatTurnSnapshotPort = activeChatTurnStore,
                    playbackController = playbackController,
                    studioRepository = studioRepository,
                    shareChatUseCase = shareChatUseCase,
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )
            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect() }
            harness.viewModel.onInputChange("Pending")
            harness.viewModel.onSendMessage()
            runCurrent()

            assertEquals(OSDA_V01_REQUIRED_MESSAGE_COUNT, harness.viewModel.uiState.value.messages.size)
            assertEquals("Pending", harness.viewModel.uiState.value.messages.last().content.text)

            processPromptGate.complete(Unit)
            collectJob.cancel()
        }
}
