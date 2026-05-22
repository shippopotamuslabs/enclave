package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Date

/**
 * Behavioral tests per [plans/FEAT-settings-data-controls/test_spec.md] Section 1 —
 * [DeleteAllChatsUseCase].
 */
@DisplayName("DeleteAllChatsUseCase")
class DeleteAllChatsUseCaseTest {
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var useCase: DeleteAllChatsUseCase

    @BeforeEach
    fun setup() {
        chatRepository = FakeChatRepository()
        useCase = DeleteAllChatsUseCase(chatRepository)
    }

    private fun chat(id: String): Chat {
        val now = Date()
        return Chat(
            id = ChatId(id),
            name = "chat-$id",
            created = now,
            lastModified = now,
        )
    }

    @Nested
    @DisplayName("happy paths")
    inner class HappyPaths {
        @Test
        @DisplayName("S1 — delete three chats, all succeed")
        fun `S1 delete three chats all succeed`() =
            runTest {
                chatRepository.setChats(
                    listOf(
                        chat("chat-1"),
                        chat("chat-2"),
                        chat("chat-3"),
                    ),
                )

                useCase()

                assertEquals(setOf("chat-1", "chat-2", "chat-3"), chatRepository.deletedChatIds)
            }

        @Test
        @DisplayName("S2 — empty repository produces no deletions")
        fun `S2 empty repository produces no deletions`() =
            runTest {
                chatRepository.setChats(emptyList())

                useCase()

                assertEquals(emptySet<String>(), chatRepository.deletedChatIds)
            }
    }

    @Nested
    @DisplayName("errors")
    inner class Errors {
        @Test
        @DisplayName("E1 — repository throws on deleteChat, exception propagates")
        fun `E1 deleteChat throws propagates`() =
            runTest {
                chatRepository.setChats(listOf(chat("chat-A")))
                chatRepository.deleteChatFailure = RuntimeException("disk full")

                val thrown =
                    assertThrows<RuntimeException> {
                        useCase()
                    }

                assertEquals("disk full", thrown.message)
            }
    }
}
