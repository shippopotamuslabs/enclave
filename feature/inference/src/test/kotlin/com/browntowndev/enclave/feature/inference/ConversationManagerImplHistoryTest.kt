// MockK is used only for LiteRT-LM SDK types (com.google.ai.edge.litertlm.*), a third-party SDK boundary.
@file:Suppress("ForbiddenMockKInTests")

package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.google.ai.edge.litertlm.Conversation
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ConversationManagerImplHistoryTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()

    private lateinit var manager: ConversationManagerImpl

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager =
            ConversationManagerImpl(
                context = context,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                loggingPort = loggingPort,
                toolExecutor = toolExecutor,
                embeddingEnginePort = FakeEmbeddingEngine(),
            )
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

            assertEquals(
                1,
                debugLogCount("History is a continuation. Reusing conversation."),
            )
            assertEquals(
                0,
                debugLogCount("History discontinuity detected"),
            )
        }

    @Test
    fun `setHistory closes native conversation when rehydrated history is still empty`() =
        runTest {
            val mockLiteRtConversation = mockk<Conversation>(relaxed = true)
            manager.conversation = mockLiteRtConversation
            manager.history = emptyList()

            manager.setHistory(emptyList())

            assertEquals(
                1,
                debugLogCount("Empty rehydrated history requires native conversation reset"),
            )
            verify(exactly = 1) { mockLiteRtConversation.close() }
            assertNull(manager.conversation)
        }

    private fun debugLogCount(messagePart: String): Int =
        ShadowLog
            .getLogsForTag("ConversationManager")
            .count { it.type == Log.DEBUG && it.msg.contains(messagePart) }
}
