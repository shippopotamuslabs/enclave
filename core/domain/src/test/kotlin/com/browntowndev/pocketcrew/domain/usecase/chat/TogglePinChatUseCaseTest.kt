package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for TogglePinChatUseCase.
 *
 * These tests verify the use case correctly forwards the pin toggle action to the repository.
 */
class TogglePinChatUseCaseTest {

    private lateinit var mockRepository: ChatRepository
    private lateinit var useCase: TogglePinChatUseCase

    @BeforeEach
    fun setup() {
        mockRepository = mockk(relaxed = true)
        useCase = TogglePinChatUseCase(mockRepository)
    }

    /**
     * Scenario: Toggling pin status delegates to repository
     *
     * Given: A valid chat ID
     * When: The use case is invoked with the chat ID
     * Then: The repository's togglePinStatus method is called with the correct ID
     */
    @Test
    fun `toggling pin status delegates to repository`() = runTest {
        // Given
        val chatId = ChatId("123")
        coEvery { mockRepository.togglePinStatus(chatId) } returns Unit

        // When
        useCase(chatId)

        // Then
        coVerify(exactly = 1) { mockRepository.togglePinStatus(chatId) }
    }

    /**
     * Scenario: Repository exceptions are propagated
     *
     * Given: The repository throws an exception when toggling pin status
     * When: The use case is invoked
     * Then: The exception is propagated to the caller
     */
    @Test
    fun `repository exceptions are propagated`() = runTest {
        // Given
        val chatId = ChatId("456")
        val expectedException = RuntimeException("Database error")
        coEvery { mockRepository.togglePinStatus(chatId) } throws expectedException

        // When / Then
        assertThrows<RuntimeException> {
            useCase(chatId)
        }

        coVerify(exactly = 1) { mockRepository.togglePinStatus(chatId) }
    }
}
