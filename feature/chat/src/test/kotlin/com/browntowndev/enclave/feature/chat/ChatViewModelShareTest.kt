package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ImportSharedChatUseCase
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelShareTest {
    private lateinit var viewModel: ChatViewModel
    private lateinit var shareChatUseCase: ShareChatUseCase
    private lateinit var importSharedChatUseCase: ImportSharedChatUseCase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        shareChatUseCase = mockk(relaxed = true)
        importSharedChatUseCase = mockk(relaxed = true)

        // Mock all dependencies
        val settingsUseCases = mockk<SettingsUseCases>(relaxed = true)
        val chatUseCases = mockk<ChatUseCases>(relaxed = true)
        val stageImageAttachmentUseCase = mockk<StageImageAttachmentUseCase>(relaxed = true)
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "chat-1"))
        val cancelInferenceUseCase = mockk<CancelInferenceUseCase>(relaxed = true)
        val inferenceLockManager = mockk<InferenceLockManager>(relaxed = true)
        val modelDisplayNameUseCase = mockk<GetModelDisplayNameUseCase>(relaxed = true)
        val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
        val toolExecutionEventPort = mockk<ToolExecutionEventPort>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val activeChatTurnSnapshotPort = mockk<ActiveChatTurnSnapshotPort>(relaxed = true)
        val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
        val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)

        every { settingsUseCases.getSettings() } returns
            flowOf(
                com.browntowndev.enclave.domain.port.repository
                    .SettingsData(),
            )
        every { settingsUseCases.assignments.getDefaultModels() } returns flowOf(emptyList())
        every { toolExecutionEventPort.events } returns MutableSharedFlow<ToolExecutionEvent>()
        every { activeChatTurnSnapshotPort.observe(any()) } returns flowOf(null)
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        coEvery { chatUseCases.getChat(any()) } returns flowOf(emptyList())
        coEvery { modelDisplayNameUseCase.invoke(any()) } returns "Test Model"
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = savedStateHandle,
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNameUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                playbackController = playbackController,
                studioRepository = studioRepository,
                shareChatUseCase = shareChatUseCase,
                importSharedChatUseCase = importSharedChatUseCase,
                chatRepository = mockk(relaxed = true),
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onShareTurn should transition to Success`() =
        runTest {
            val messageId = MessageId("msg-1")
            val shareUrl = "https://share.url/123"
            val sharedChat = SharedChat("123", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = shareUrl)

            coEvery { shareChatUseCase(any()) } returns Result.success(sharedChat)

            viewModel.uiState.test {
                // Initial state
                assertEquals(ShareState.Idle, awaitItem().shareState)

                // When
                viewModel.onShareClick(messageId)
                runCurrent()
                assertEquals(ShareState.ShowingSheet, awaitItem().shareState)

                viewModel.onShareTurn()
                runCurrent()

                // Wait for Success state
                var lastState = awaitItem().shareState
                while (lastState !is ShareState.Success) {
                    lastState = awaitItem().shareState
                }
                assertEquals(shareUrl, (lastState as ShareState.Success).shareUrl)
            }
        }

    @Test
    fun `onShareFullChat should transition to Success`() =
        runTest {
            val shareUrl = "https://share.url/full"
            val sharedChat = SharedChat("full", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = shareUrl)

            coEvery { shareChatUseCase(any()) } returns Result.success(sharedChat)

            viewModel.uiState.test {
                assertEquals(ShareState.Idle, awaitItem().shareState)

                // When
                viewModel.onShareFullChat()
                runCurrent()

                // Wait for Success state
                var lastState = awaitItem().shareState
                while (lastState !is ShareState.Success) {
                    lastState = awaitItem().shareState
                }
                assertEquals(shareUrl, (lastState as ShareState.Success).shareUrl)
            }
        }

    @Test
    fun `onLoadSharedChat should set error state when getSharedChat fails`() =
        runTest {
            coEvery { shareChatUseCase.getSharedChat(any(), any()) } returns
                Result.failure(Exception("Chat not found"))

            viewModel.uiState.test {
                awaitItem()

                viewModel.onLoadSharedChat("bad-id")
                runCurrent()

                var lastState = awaitItem().shareState
                while (lastState !is ShareState.Error) {
                    lastState = awaitItem().shareState
                }
                assertEquals("Chat not found", (lastState as ShareState.Error).message)
            }
        }

    @Test
    fun `onLoadSharedChat should emit ImportedChat event with chatId`() =
        runTest {
            val sharedChat =
                SharedChat(
                    id = "123",
                    messages =
                        listOf(
                            com.browntowndev.enclave.domain.model.share
                                .SharedMessage("user", "Hello", 0),
                        ),
                    createdAt = 0,
                    expiresAt = 0,
                    shareUrl = "",
                )
            val expectedChatId = ChatId("imported-chat-id")

            coEvery { shareChatUseCase.getSharedChat("123", null) } returns Result.success(sharedChat)
            coEvery { importSharedChatUseCase(sharedChat) } returns expectedChatId

            viewModel.shareUiEvents.test {
                viewModel.onLoadSharedChat("123")
                runCurrent()

                val event = awaitItem()
                assertEquals(ShareUiEvent.ImportedChat(expectedChatId), event)
            }
        }
}
