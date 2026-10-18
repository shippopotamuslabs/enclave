package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.share.SharedChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var shareChatUseCase: FakeShareChatUseCase
    private lateinit var importSharedChatUseCase: FakeImportSharedChatUseCase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        shareChatUseCase = FakeShareChatUseCase()
        importSharedChatUseCase = FakeImportSharedChatUseCase()

        val settingsUseCases = FakeChatSettingsUseCases()
        val chatUseCases = FakeChatUseCases()
        val stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase()
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "chat-1"))
        val cancelInferenceUseCase = FakeCancelInferenceUseCase()
        val inferenceLockManager = FakeInferenceLockManager()
        val modelDisplayNameUseCase = FakeGetModelDisplayNameUseCase()
        val activeModelProvider = FakeActiveModelProvider()
        val toolExecutionEventPort = FakeToolExecutionEventPort()
        val errorHandler = FakeViewModelErrorHandler()
        val loggingPort = FakeLoggingPort()
        val activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort()
        val playbackController = FakeTtsPlaybackController()
        val studioRepository = FakeStudioRepository()

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
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
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

            shareChatUseCase.result = Result.success(sharedChat)

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

            shareChatUseCase.result = Result.success(sharedChat)

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
            shareChatUseCase.getSharedChatResult =
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

            shareChatUseCase.getSharedChatResult = Result.success(sharedChat)
            importSharedChatUseCase.result = expectedChatId

            viewModel.shareUiEvents.test {
                viewModel.onLoadSharedChat("123")
                runCurrent()

                val event = awaitItem()
                assertEquals(ShareUiEvent.ImportedChat(expectedChatId), event)
            }
            assertEquals(listOf(sharedChat), importSharedChatUseCase.calls)
        }
}
