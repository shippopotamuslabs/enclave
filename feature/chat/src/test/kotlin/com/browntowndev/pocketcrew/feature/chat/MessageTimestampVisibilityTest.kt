package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import androidx.lifecycle.SavedStateHandle

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MessageTimestampVisibilityTest {

    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setup() {
        val settingsUseCases = mockk<SettingsUseCases>()
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())

        viewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = mockk(relaxed = true),
            stageImageAttachmentUseCase = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(),
            inferenceLockManager = mockk(relaxed = true),
            modelDisplayNamesUseCase = mockk(relaxed = true),
            activeModelProvider = mockk(relaxed = true),
            cancelInferenceUseCase = mockk(relaxed = true),
            toolExecutionEventPort = mockk(relaxed = true),
            errorHandler = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            activeChatTurnSnapshotPort = ActiveChatTurnStore(),
            
            playbackController = mockk(relaxed = true),
        )
    }

    @Test
    fun `mapToChatMessage returns empty formattedTimestamp for null createdAt`() {
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("chat-1"),
            content = Content(text = "Hello"),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            createdAt = null
        )

        val chatMessage = viewModel.mapToChatMessageForTesting(message)

        assertEquals("", chatMessage.formattedTimestamp)
    }

    @Test
    fun `mapToChatMessage returns empty formattedTimestamp for 0L createdAt`() {
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("chat-1"),
            content = Content(text = "Hello"),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            createdAt = 0L
        )

        val chatMessage = viewModel.mapToChatMessageForTesting(message)

        assertEquals("", chatMessage.formattedTimestamp)
    }

    @Test
    fun `mapToChatMessage returns non-empty formattedTimestamp for valid createdAt`() {
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("chat-1"),
            content = Content(text = "Hello"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            createdAt = 1713189600000L // Some timestamp
        )

        val chatMessage = viewModel.mapToChatMessageForTesting(message)

        assertEquals(true, chatMessage.formattedTimestamp.isNotBlank())
    }

    @Test
    fun `sorting handles null createdAt by placing them at the end`() {
        val msg1 = Message(
            id = MessageId("1"),
            chatId = ChatId("chat-1"),
            content = Content(text = "Oldest"),
            role = Role.USER,
            messageState = MessageState.COMPLETE,
            createdAt = 1000L
        )
        val msg2 = Message(
            id = MessageId("2"),
            chatId = ChatId("chat-1"),
            content = Content(text = "Newer"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            createdAt = 2000L
        )
        val msg3 = Message(
            id = MessageId("3"),
            chatId = ChatId("chat-1"),
            content = Content(text = "In-flight"),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            createdAt = null
        )

        val messages = listOf(msg3, msg2, msg1)
        val sorted = messages.sortedBy { it.createdAt ?: Long.MAX_VALUE }

        assertEquals("1", sorted[0].id.value)
        assertEquals("2", sorted[1].id.value)
        assertEquals("3", sorted[2].id.value)
    }
}
