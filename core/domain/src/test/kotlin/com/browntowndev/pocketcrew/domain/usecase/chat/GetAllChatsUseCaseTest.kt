package com.browntowndev.pocketcrew.domain.usecase.chat

import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * TDD Red Phase Tests for GetAllChatsUseCase.
 *
 * These tests verify the use case correctly forwards data from the repository.
 * Expected values come from scenario Givens, NOT from implementation spec.
 */
class GetAllChatsUseCaseTest {

    private lateinit var mockRepository: ChatRepository

    private val testDate = Date(1700_000_000_000L)

    private fun createTestChat(
        id: ChatId,
        name: String,
        pinned: Boolean = false
    ): Chat = Chat(
        id = id,
        name = name,
        created = testDate,
        lastModified = testDate,
        pinned = pinned
    )

    @BeforeEach
    fun setup() {
        mockRepository = mockk(relaxed = true)
        every { mockRepository.getAllChats() } returns MutableStateFlow<List<Chat>>(emptyList())
    }

    // ========================================================================
    // Suite A: GetAllChatsUseCase Tests
    // ========================================================================

    /**
     * Scenario A1: Returns chats from repository
     *
     * Given: ChatRepository.getAllChats() returns Flow with 3 chats
     * When: GetAllChatsUseCase() is invoked
     * Then: Flow emits exactly 3 Chat objects
     */
    @Test
    fun `A1 returns chats from repository`() = runTest {
        // Given: Repository returns Flow with 3 chats
        val testChats = listOf(
            createTestChat(ChatId("1"), "Alpha"),
            createTestChat(ChatId("2"), "Beta"),
            createTestChat(ChatId("3"), "Gamma")
        )
        val chatFlow = MutableStateFlow<List<Chat>>(testChats)
        every { mockRepository.getAllChats() } returns chatFlow

        // When: Use case is invoked
        val useCase = GetAllChatsUseCase(mockRepository)

        // Then: Flow emits exactly 3 Chat objects
        useCase().test {
            val emittedChats = awaitItem()
            assertEquals(3, emittedChats.size)
            assertEquals("Alpha", emittedChats[0].name)
            assertEquals("Beta", emittedChats[1].name)
            assertEquals("Gamma", emittedChats[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Scenario A2: Empty database returns empty list
     *
     * Given: Repository returns Flow with empty list
     * When: Use case is invoked
     * Then: Flow emits empty list
     */
    @Test
    fun `A2 empty database returns empty list`() = runTest {
        // Given: Repository returns Flow with empty list
        val emptyFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockRepository.getAllChats() } returns emptyFlow

        // When: Use case is invoked
        val useCase = GetAllChatsUseCase(mockRepository)

        // Then: Flow emits empty list
        useCase().test {
            val emittedChats = awaitItem()
            assertEquals(0, emittedChats.size)
            assertEquals(emptyList<Chat>(), emittedChats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Scenario A3: Flow emissions are forwarded
     *
     * Given: Repository flow emits twice with different lists
     * When: Use case is invoked, flow emits twice
     * Then: Use case flow receives both emissions
     */
    @Test
    fun `A3 flow emissions are forwarded`() = runTest {
        // Given: Repository flow that can emit multiple times
        val chatFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockRepository.getAllChats() } returns chatFlow

        val useCase = GetAllChatsUseCase(mockRepository)

        // When/Then: Flow receives both emissions
        useCase().test {
            // First emission - empty
            assertEquals(0, awaitItem().size)

            // Emit second list with 2 chats
            chatFlow.value = listOf(
                createTestChat(ChatId("1"), "Chat A"),
                createTestChat(ChatId("2"), "Chat B")
            )

            // Second emission - 2 chats
            val secondEmission = awaitItem()
            assertEquals(2, secondEmission.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Scenario A4: Repository error propagates
     *
     * Given: Repository getAllChats() returns Flow that errors with RuntimeException
     * When: Flow is collected
     * Then: Error propagates through use case
     */
    @Test
    fun `A4 repository error propagates`() = runTest {
        // Given: Repository that throws RuntimeException via flow error
        every { mockRepository.getAllChats() } returns flow {
            throw RuntimeException("DB Error")
        }

        val useCase = GetAllChatsUseCase(mockRepository)

        // When/Then: Error propagates through use case
        useCase().test {
            awaitError()
        }
    }
}
