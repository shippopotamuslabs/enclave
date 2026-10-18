package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Date

/**
 * Tests for TogglePinChatUseCase.
 *
 * These tests verify the use case correctly forwards the pin toggle action to the repository.
 */
class TogglePinChatUseCaseTest {
    private lateinit var repository: FakeChatRepository
    private lateinit var useCase: TogglePinChatUseCase

    @BeforeEach
    fun setup() {
        repository = FakeChatRepository()
        useCase = TogglePinChatUseCase(repository)
    }

    /**
     * Scenario: Toggling pin status delegates to repository
     *
     * Given: A valid chat ID
     * When: The use case is invoked with the chat ID
     * Then: The repository's togglePinStatus method is called with the correct ID
     */
    @Test
    fun `toggling pin status delegates to repository`() =
        runTest {
            // Given
            val chatId = ChatId("123")
            val now = Date()
            repository.seedChats(listOf(Chat(id = chatId, name = "Chat", created = now, lastModified = now)))

            // When
            useCase(chatId)

            // Then
            assertEquals(listOf(chatId), repository.togglePinCalls)
        }

    /**
     * Scenario: Repository exceptions are propagated
     *
     * Given: The repository throws an exception when toggling pin status
     * When: The use case is invoked
     * Then: The exception is propagated to the caller
     */
    @Test
    fun `repository exceptions are propagated`() =
        runTest {
            // Given
            val chatId = ChatId("456")
            val expectedException = RuntimeException("Database error")
            repository.togglePinFailure = expectedException

            // When / Then
            assertThrows<RuntimeException> {
                useCase(chatId)
            }

            assertEquals(listOf(chatId), repository.togglePinCalls)
        }
}
