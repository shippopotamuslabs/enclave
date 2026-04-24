package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConversationManagerImplHistoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    private lateinit var manager: ConversationManagerImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        manager = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `setHistory identifies turn 2 as a continuation when starting from empty history`() = runTest {
        // Given
        val mockLiteRtConversation = mockk<Conversation>(relaxed = true)
        setPrivateField(manager, "conversation", mockLiteRtConversation)
        
        // Initial state is empty history
        setPrivateField(manager, "history", emptyList<ChatMessage>())

        // When turn 2 history is set (initial user message + assistant response)
        val turn2History = listOf(
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there!")
        )
        manager.setHistory(turn2History)

        // Then
        verify(exactly = 1) { 
            Log.d("ConversationManager", match { it.contains("History is a continuation. Reusing conversation.") })
        }
        verify(exactly = 0) { 
            Log.d("ConversationManager", match { it.contains("History discontinuity detected") })
        }
    }

    private fun setPrivateField(obj: Any, fieldName: String, value: Any?) {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }
}
