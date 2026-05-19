package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class MessageTimestampVisibilityTest {
    private lateinit var viewModel: ChatViewModel
    private val settingsUseCases: SettingsUseCases = mockk(relaxed = true)
    private val chatUseCases: ChatUseCases = mockk(relaxed = true)
    private val shareChatUseCase: ShareChatUseCase = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { settingsUseCases.getSettings() } returns flowOf(SettingsData())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())

        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(),
                cancelInferenceUseCase = mockk(relaxed = true),
                inferenceLockManager = mockk(relaxed = true),
                modelDisplayNamesUseCase = mockk(relaxed = true),
                activeModelProvider = mockk(relaxed = true),
                toolExecutionEventPort = mockk(relaxed = true),
                errorHandler = mockk(relaxed = true),
                activeChatTurnSnapshotPort = mockk(relaxed = true),
                playbackController = mockk(relaxed = true),
                studioRepository = mockk(relaxed = true),
                shareChatUseCase = shareChatUseCase,
                chatRepository = mockk(relaxed = true),
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = mockk(relaxed = true),
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
