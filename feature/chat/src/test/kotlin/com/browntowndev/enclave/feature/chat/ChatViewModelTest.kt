package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeUiSelectionRepository
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportDestination
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportFormat
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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

    private val settingsUseCases = FakeChatSettingsUseCases()
    private val chatUseCases = FakeChatUseCases()
    private val stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase()
    private val cancelInferenceUseCase = FakeCancelInferenceUseCase()
    private val inferenceLockManager = FakeInferenceLockManager()
    private val toolExecutionEventPort = FakeToolExecutionEventPort()
    private val modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()
    private val errorHandler = FakeViewModelErrorHandler()
    private val loggingPort = FakeLoggingPort()
    private val activeChatTurnStore = FakeActiveChatTurnSnapshotPort()
    private val playbackController = FakeTtsPlaybackController()
    private val studioRepository = FakeStudioRepository()
    private val shareChatUseCase = FakeShareChatUseCase()
    private val chatRepository = FakeChatRepository()
    private val exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase()
    private val activeModelProvider = FakeActiveModelProvider()
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var defaultModelsFlow: MutableStateFlow<List<DefaultModelAssignment>>

    @BeforeEach
    fun setUp() {
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
        activeModelProvider.configuration = visionConfig

        defaultModelsFlow = MutableStateFlow(emptyList())
        settingsUseCases.assignments.getDefaultModels.flow = defaultModelsFlow

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

    private fun createChatViewModel(uiSelectionRepository: UiSelectionRepository): ChatViewModel =
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
            uiSelectionRepository = uiSelectionRepository,
        )

    @Test
    fun `onSendMessage error invokes errorHandler`() =
        runTest {
            // Given
            val input = "Hello"
            chatViewModel.onInputChange(input)

            val exception = IOException("Network error")
            chatUseCases.submitPromptUseCase.failure = exception

            // When
            chatViewModel.onSendMessage()

            // Then
            // Verify errorHandler was called with correct parameters
            assertEquals(listOf("InferenceOrchestrator"), errorHandler.handledTags)
            assertEquals(listOf("Failed to send message"), errorHandler.handledMessages)
            assertEquals(listOf(exception), errorHandler.handledThrowables)
            assertEquals(
                listOf("Could not send message. Please try again."),
                errorHandler.handledUserMessages,
            )
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
            exportArtifactDocumentsUseCase.result =
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

            val exportCommands = exportArtifactDocumentsUseCase.calls
            assertEquals(1, exportCommands.size)
            assertEquals(documentJson, exportCommands.single().documentJson)
            assertEquals("Doc", exportCommands.single().baseTitle)
            assertEquals(ArtifactExportFormat.PDF, exportCommands.single().format)
            assertEquals(ArtifactExportDestination.DOWNLOAD, exportCommands.single().destination)
            assertTrue(chatRepository.getMessagesForChatCalls.isEmpty())
            assertEquals(ArtifactExportSheetState.Hidden, chatViewModel.uiState.value.artifactExportSheet)
            collectJob.cancel()
        }

    @Test
    fun `onConfirmArtifactExport keeps sheet visible with error when document is blank`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            val chatId = ChatId("chat-blank-export")
            savedStateHandle["chatId"] = chatId.value
            exportArtifactDocumentsUseCase.result =
                Result.success(ArtifactExportOutcome.Downloaded(emptyList()))
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

            assertTrue(exportArtifactDocumentsUseCase.calls.isEmpty())
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
            exportArtifactDocumentsUseCase.result =
                Result.success(ArtifactExportOutcome.Downloaded(listOf("Doc.pdf")))
            chatRepository.seedMessages(
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

            val exportCommands = exportArtifactDocumentsUseCase.calls
            assertEquals(1, exportCommands.size)
            assertTrue(exportCommands.single().documentJson.contains("Body"))
            assertEquals("Doc", exportCommands.single().baseTitle)
            assertEquals(ArtifactExportFormat.PDF, exportCommands.single().format)
            assertEquals(ArtifactExportDestination.DOWNLOAD, exportCommands.single().destination)
            assertEquals(1, chatRepository.getMessagesForChatCalls.count { it == chatId })
            collectJob.cancel()
        }

    @Test
    fun `onConfirmArtifactExport shows error when chat id missing for hydration`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            savedStateHandle["chatId"] = null
            exportArtifactDocumentsUseCase.result =
                Result.success(ArtifactExportOutcome.Downloaded(emptyList()))

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

            assertTrue(chatRepository.getMessagesForChatCalls.isEmpty())
            assertTrue(exportArtifactDocumentsUseCase.calls.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `onMicClick starts listening when Idle`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            chatViewModel.onMicClick()
            assertEquals(1, chatUseCases.listenToSpeechUseCase.stopSignals.size)
            collectJob.cancel()
        }

    @Test
    fun `onMicClick sets stop signal when Listening`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            chatViewModel.onMicClick()
            chatUseCases.listenToSpeechUseCase.events.emit(SpeechState.Listening(0f))
            runCurrent()
            chatViewModel.onMicClick()
            runCurrent()
            // Verify that the stop signal was sent. The actual state change to Idle will happen
            // when the use case processes the signal and completes.
            assertTrue(chatUseCases.listenToSpeechUseCase.stopSignals.any { it.value })
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
            playbackController.playbackFlow =
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            assertTrue(playbackController.playCalls.contains(text))
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
            playbackController.playbackFlow =
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
            playbackController.playbackFlow =
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
            playbackController.playbackFlow =
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

            assertTrue(playbackController.stopCount >= 1)
            collectJob.cancel()
        }

    @Test
    fun `onPauseTts calls pause on playbackController`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.TTS,
                        ttsProviderId = TtsProviderId("tts-provider"),
                    ),
                )
            runCurrent()

            chatViewModel.onPauseTts()

            assertTrue(playbackController.pauseCount >= 1)
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
            playbackController.playbackFlow =
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text1)
            runCurrent()
            chatViewModel.onPlayTts(text2)
            runCurrent()

            // stop() should be called at least once (from the cancel-before-start)
            assertTrue(playbackController.stopCount >= 1)
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
            playbackController.playbackFlow =
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            chatViewModel.onPlayTts(text)
            runCurrent()

            // Verify the playback controller was called
            assertTrue(playbackController.playCalls.contains(text))
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
            playbackController.playbackFlow =
                flow {
                    emit(TtsPlaybackStatus.Playing)
                    delay(10_000) // Keep alive
                }

            chatViewModel.onPlayTts(text)
            runCurrent()
            assertTrue(chatViewModel.uiState.value.isPlayingTts)

            chatViewModel.onStopTts()
            runCurrent()

            assertTrue(playbackController.stopCount >= 1)
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
            assertTrue(playbackController.playCalls.isEmpty())
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
            playbackController.playbackFlow =
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
            chatUseCases.processFileAttachmentUseCase.result = metadata

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
            chatUseCases.processFileAttachmentUseCase.result = metadata

            // Mock prompt result
            val chatId = ChatId("chat")
            mockSubmitPrompt(
                chatUseCases = chatUseCases,
                chatId = chatId,
                userMessageId = MessageId("user"),
            )

            chatViewModel.onFileSelected(uri)
            advanceUntilIdle()

            chatViewModel.onInputChange("Hello")
            chatViewModel.onSendMessage()
            advanceUntilIdle()

            val submitPromptRequests = chatUseCases.submitPromptUseCase.calls
            assertEquals(1, submitPromptRequests.size)
            assertEquals("Hello", submitPromptRequests.single().message.content.text)
            assertEquals(1, submitPromptRequests.single().message.content.fileAttachments.size)
            assertEquals("test.txt", submitPromptRequests.single().message.content.fileAttachments[0].name)
            assertEquals("Extracted content", submitPromptRequests.single().message.content.fileAttachments[0].content)
        }

    @Test
    fun `UI state reconciles media URIs from StudioRepository`() =
        runTest {
            val chatId = ChatId("chat-1")
            val messageId = MessageId("msg-1")
            val assetId = "asset-1"
            val cacheUri = "file:///cache/test.jpg"
            val persistentUri = "file:///files/test.jpg"

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
            chatUseCases.getChatUseCase.flowsByChatId[chatId] = messagesFlow

            val savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value))
            chatViewModel =
                ChatViewModelTestSupport.createViewModel(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = savedStateHandle,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = FakeActiveModelProvider(),
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

            // Initial state should use cache URI
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
            runCurrent()

            val initialMessage =
                chatViewModel.uiState.value.messages
                    .first()
            assertEquals(cacheUri, initialMessage.content.generatedMediaUris.first())

            // Simulate asset being saved to Gallery (URI change)
            studioRepository.mediaState.value =
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
            chatUseCases.getChatUseCase.flow = dbFlow

            val safetyGate = CompletableDeferred<Unit>()
            chatUseCases.submitPromptUseCase.behavior = {
                safetyGate.await()
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
                    activeModelProvider = FakeActiveModelProvider(),
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

            safetyGate.complete(Unit)
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
            chatUseCases.getChatUseCase.flowsByChatId[chatId] = dbFlow
            mockSubmitPrompt(
                chatUseCases = chatUseCases,
                userMessageId = MessageId("opt-1"),
                chatId = chatId,
            )

            val harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = FakeActiveModelProvider(),
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
            assertTrue(
                (harness.inferenceOrchestrator as RecordingInferenceOrchestrator)
                    .clearOptimisticUserCalls >= 1,
            )

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
                    activeModelProvider = FakeActiveModelProvider(),
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
            chatUseCases.submitPromptUseCase.failure = IOException("Network error")
            harness.viewModel.onSendMessage()
            advanceUntilIdle()

            assertEquals(1, errorHandler.handledTags.size)
            assertEquals("InferenceOrchestrator", errorHandler.handledTags.single())
            assertEquals("Failed to send message", errorHandler.handledMessages.single())
            assertTrue(errorHandler.handledThrowables.single() is IOException)
            assertEquals("Could not send message. Please try again.", errorHandler.handledUserMessages.single())
            assertNull(harness.inferenceOrchestrator.optimisticUserMessage.value)
        }

    @Test
    @DisplayName("OSDA-M-02 — merge wired shows optimistic user when DB is empty")
    fun osdaM02_bypassMergeGuard() =
        runTest {
            val dbFlow = MutableStateFlow<List<Message>>(emptyList())
            chatUseCases.getChatUseCase.flow = dbFlow

            val safetyGate = CompletableDeferred<Unit>()
            chatUseCases.submitPromptUseCase.behavior = {
                safetyGate.await()
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
                    activeModelProvider = FakeActiveModelProvider(),
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
            assertEquals(
                "Pending",
                harness.viewModel.uiState.value.messages
                    .last()
                    .content.text,
            )

            safetyGate.complete(Unit)
            collectJob.cancel()
        }

    @Test
    fun `init seeds coordinator with persisted non-default chat mode`() =
        runTest {
            val uiSelectionRepository = FakeUiSelectionRepository()
            uiSelectionRepository.emitSelectedChatMode(Mode.THINKING)
            val vm = createChatViewModel(uiSelectionRepository = uiSelectionRepository)
            val collectJob = backgroundScope.launch { vm.uiState.collect() }
            advanceUntilIdle()

            assertEquals(ChatModeUi.THINKING, vm.uiState.value.selectedMode)
            collectJob.cancel()
        }

    @Test
    fun `onModeChange persists the selected mode`() =
        runTest {
            val uiSelectionRepository = FakeUiSelectionRepository()
            val vm = createChatViewModel(uiSelectionRepository = uiSelectionRepository)
            vm.onModeChange(ChatModeUi.THINKING)
            advanceUntilIdle()

            assertEquals(listOf(Mode.THINKING), uiSelectionRepository.setChatModeCalls)
        }
}
