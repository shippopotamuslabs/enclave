package com.browntowndev.pocketcrew.feature.history

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class HistoryMappersTest {
    @Test
    fun `toHistoryChat syncingChat forwardsIsSyncingTrue`() {
        val chat =
            Chat(
                id = ChatId("chat-1"),
                name = "Syncing...",
                created = Date(0),
                lastModified = Date(0),
                pinned = false,
                isSyncing = true,
            )

        val historyChat = chat.toHistoryChat()

        assertTrue("Expected isSyncing=true to be forwarded", historyChat.isSyncing)
    }

    @Test
    fun `toHistoryChat normalChat isSyncingFalse`() {
        val chat =
            Chat(
                id = ChatId("chat-2"),
                name = "Normal Chat",
                created = Date(1_000_000L),
                lastModified = Date(1_000_000L),
                pinned = false,
                isSyncing = false,
            )

        val historyChat = chat.toHistoryChat()

        assertFalse("Expected isSyncing=false to be forwarded", historyChat.isSyncing)
    }
}
