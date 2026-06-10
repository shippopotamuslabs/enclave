package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.share.ShareOption
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.usecase.share.ImportSharedChatUseCase
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.chat.ShareState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatShareCoordinatorTest {
    @Test
    fun onActiveChatChanged_clearsStaleShareUrlCache() =
        runTest {
            val shareChatUseCase = mockk<ShareChatUseCase>()
            val coordinator = ChatShareCoordinator(shareChatUseCase, mockk(relaxed = true))
            val chatA = ChatId("chat-a")
            val messageId = MessageId("msg-1")
            val option = ShareOption.SingleTurn(chatA, messageId)

            coEvery { shareChatUseCase(option) } returns
                Result.success(
                    SharedChat(
                        id = "share-1",
                        messages = emptyList(),
                        createdAt = 0,
                        expiresAt = 0,
                        shareUrl = "https://share.example/1",
                    ),
                )

            coordinator.onShareClick(messageId, chatA)
            coordinator.onShareTurn(this, chatA)
            advanceUntilIdle()
            assertTrue(coordinator.shareState.value is ShareState.Success)

            coordinator.onActiveChatChanged(ChatId("chat-b"))
            coordinator.onShareClick(messageId, chatA)

            assertEquals(ShareState.ShowingSheet, coordinator.shareState.value)
        }
}