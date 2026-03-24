package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * TDD Red Phase Tests for ChatRepositoryImpl.
 *
 * These tests verify the repository correctly maps entities to domain models
 * and handles togglePinStatus operations.
 * Expected values come from scenario Givens, NOT from implementation spec.
 */
class ChatRepositoryImplTest {

    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var repository: ChatRepositoryImpl

    private val testDate = Date(1700_000_000_000L)

    @BeforeEach
    fun setup() {
        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        repository = ChatRepositoryImpl(chatDao, messageDao)
    }

    // ========================================================================
    // Suite D: Repository Implementation Tests
    // ========================================================================

    /**
     * Scenario D1: Repository maps Entity to Domain
     *
     * Given: ChatEntity with id=1, name="Test", pinned=true
     * When: getAllChats() called
     * Then: Flow emits Chat with matching id, name, pinned
     */
    @Test
    fun `D1 maps Entity to Domain correctly`() = runTest {
        // Given: ChatEntity with id=1, name="Test", pinned=true
        val entity = ChatEntity(
            id = 1,
            name = "Test",
            created = testDate,
            lastModified = testDate,
            pinned = true
        )
        coEvery { chatDao.getAllChats() } returns kotlinx.coroutines.flow.flowOf(listOf(entity))

        // When: getAllChats() called
        val result = repository.getAllChats().first()

        // Then: Flow emits Chat with matching id, name, pinned
        assertEquals(1, result.size)
        val chat = result.first()
        assertEquals(1L, chat.id)
        assertEquals("Test", chat.name)
        assertTrue(chat.pinned)
        assertEquals(testDate, chat.created)
        assertEquals(testDate, chat.lastModified)
    }

    /**
     * Scenario D2: togglePinStatus updates the database
     *
     * Given: Chat id=5 with pinned=false
     * When: togglePinStatus(5) called
     * Then: Chat id=5 has pinned=true in DB
     */
    @Test
    fun `D2 togglePinStatus updates database from false to true`() = runTest {
        // Given: Chat id=5 with pinned=false (existing in DB)
        val entity = ChatEntity(
            id = 5,
            name = "Test Chat",
            created = testDate,
            lastModified = testDate,
            pinned = false
        )
        coEvery { chatDao.getChatById(5) } returns entity
        coEvery { chatDao.updatePinStatus(5) } returns 1

        // When: togglePinStatus(5) called
        repository.togglePinStatus(5)

        // Then: updatePinStatus was called on the DAO
        coVerify { chatDao.updatePinStatus(5) }
    }

    /**
     * Scenario D3: togglePinStatus toggles from true to false
     *
     * Given: Chat id=10 with pinned=true
     * When: togglePinStatus(10) called
     * Then: Chat id=10 has pinned=false in DB
     */
    @Test
    fun `D3 togglePinStatus toggles from true to false`() = runTest {
        // Given: Chat id=10 with pinned=true
        val entity = ChatEntity(
            id = 10,
            name = "Pinned Chat",
            created = testDate,
            lastModified = testDate,
            pinned = true
        )
        coEvery { chatDao.getChatById(10) } returns entity
        coEvery { chatDao.updatePinStatus(10) } returns 1

        // When: togglePinStatus(10) called
        repository.togglePinStatus(10)

        // Then: updatePinStatus was called on the DAO
        coVerify { chatDao.updatePinStatus(10) }
    }
}
