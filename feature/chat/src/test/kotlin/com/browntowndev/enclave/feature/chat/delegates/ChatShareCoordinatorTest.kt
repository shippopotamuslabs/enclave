package com.browntowndev.enclave.feature.chat.delegates

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.usecase.share.ImportSharedChatUseCase
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.chat.ShareState
import com.browntowndev.enclave.feature.chat.ShareUiEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatShareCoordinatorTest {
    private lateinit var shareChatUseCase: ShareChatUseCase
    private lateinit var importSharedChatUseCase: ImportSharedChatUseCase
    private lateinit var coordinator: ChatShareCoordinator
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        shareChatUseCase = mockk(relaxed = true)
        importSharedChatUseCase = mockk(relaxed = true)
        coordinator = ChatShareCoordinator(shareChatUseCase, importSharedChatUseCase)
    }

    @Test
    fun `onLoadSharedChat emits ImportedChat with resolved chatId`() =
        runTest {
            val sharedChat = SharedChat("share-1", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = "")
            val importedChatId = ChatId("imported-chat")
            coEvery { shareChatUseCase.getSharedChat("share-1", "key123") } returns Result.success(sharedChat)
            coEvery { importSharedChatUseCase(sharedChat) } returns importedChatId

            coordinator.shareUiEvents.test {
                coordinator.onLoadSharedChat(this@runTest, "share-1", "key123")

                assertEquals(ShareUiEvent.ImportedChat(importedChatId), awaitItem())
            }
        }

    @Test
    fun `onLoadSharedChat sets error state when getSharedChat fails`() =
        runTest {
            coEvery { shareChatUseCase.getSharedChat(any(), any()) } returns Result.failure(Exception("Network error"))

            coordinator.onLoadSharedChat(this, "share-1", null)
            advanceUntilIdle()

            val state = coordinator.shareState.value
            assertTrue(state is ShareState.Error)
        }

    @Test
    fun `onLoadSharedChat does nothing when sharedChat is null`() =
        runTest {
            coEvery { shareChatUseCase.getSharedChat(any(), any()) } returns Result.success(null)

            coordinator.onLoadSharedChat(this, "share-1", null)
            advanceUntilIdle()

            coVerify(exactly = 0) { importSharedChatUseCase(any()) }
            assertEquals(ShareState.Idle, coordinator.shareState.value)
        }

    @Test
    fun `onLoadSharedChat uses error message from exception when available`() =
        runTest {
            coEvery { shareChatUseCase.getSharedChat(any(), any()) } returns
                Result.failure(Exception("Custom error message"))

            coordinator.onLoadSharedChat(this, "share-1", null)
            advanceUntilIdle()

            val state = coordinator.shareState.value as ShareState.Error
            assertEquals("Custom error message", state.message)
        }

    @Test
    fun `onLoadSharedChat calls importSharedChatUseCase with decrypted shared chat`() =
        runTest {
            val sharedChat = SharedChat("share-1", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = "")
            coEvery { shareChatUseCase.getSharedChat("share-1", "key") } returns Result.success(sharedChat)
            coEvery { importSharedChatUseCase(sharedChat) } returns ChatId("chat-id")

            coordinator.shareUiEvents.test {
                coordinator.onLoadSharedChat(this@runTest, "share-1", "key")
                awaitItem()
            }

            coVerify { importSharedChatUseCase(sharedChat) }
        }
}
