package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class MessageTimestampVisibilityTest {
    private lateinit var viewModel: ChatViewModel
    private val settingsUseCases = FakeChatSettingsUseCases()
    private val chatUseCases = FakeChatUseCases()
    private val shareChatUseCase = FakeShareChatUseCase()

    @BeforeEach
    fun setup() {
        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(),
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                inferenceLockManager = FakeInferenceLockManager(),
                modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase(),
                activeModelProvider = FakeActiveModelProvider(),
                toolExecutionEventPort = FakeToolExecutionEventPort(),
                errorHandler = FakeViewModelErrorHandler(),
                activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort(),
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = shareChatUseCase,
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = fakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
    }

    @Test
    fun `mapToChatMessageForTesting preserves non-empty timestamp`() {
        val message =
            Message(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                role = Role.USER,
                content = Content(text = "Hello"),
                createdAt = 1_700_000_000_000L,
            )

        val chatMessage = viewModel.mapToChatMessageForTesting(message)

        assertEquals(true, chatMessage.formattedTimestamp.isNotEmpty())
    }

    @Test
    fun `mapToChatMessageForTesting returns empty string for null timestamp`() {
        val message =
            Message(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                role = Role.USER,
                content = Content(text = "Hello"),
                createdAt = null,
            )

        val chatMessage = viewModel.mapToChatMessageForTesting(message)

        assertEquals("", chatMessage.formattedTimestamp)
    }
}
