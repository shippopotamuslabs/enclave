package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
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

        manager =
            ConversationManagerImpl(
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
    fun `setHistory identifies turn 2 as a continuation when starting from empty history`() =
        runTest {
            val mockLiteRtConversation = mockk<Conversation>(relaxed = true)
            manager.conversation = mockLiteRtConversation
            manager.history = emptyList()

            val turn2History =
                listOf(
                    ChatMessage(Role.USER, "Hello"),
                    ChatMessage(Role.ASSISTANT, "Hi there!"),
                )
            manager.setHistory(turn2History)

            verify(exactly = 1) {
                Log.d("ConversationManager", match { it.contains("History is a continuation. Reusing conversation.") })
            }
            verify(exactly = 0) {
                Log.d("ConversationManager", match { it.contains("History discontinuity detected") })
            }
        }

    @Test
    fun `setHistory closes native conversation when rehydrated history is still empty`() =
        runTest {
            val mockLiteRtConversation = mockk<Conversation>(relaxed = true)
            manager.conversation = mockLiteRtConversation
            manager.history = emptyList()

            manager.setHistory(emptyList())

            verify(exactly = 1) {
                Log.d("ConversationManager", match { it.contains("Empty rehydrated history requires native conversation reset") })
            }
            verify(exactly = 1) { mockLiteRtConversation.close() }
            assertNull(manager.conversation)
        }
}
