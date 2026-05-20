package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.feature.chat.delegates.maybeAcknowledgeCompletedTurn
import com.browntowndev.pocketcrew.feature.chat.delegates.resolveActiveTurnSnapshotObserveKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveTurnSnapshotObserveKeyTest {
    private val chatId = ChatId("chat")
    private val assistantId = MessageId("assistant")
    private val turnKey = ActiveChatTurnKey(chatId, assistantId)

    @Test
    fun `requested turn key is not suppressed by acknowledged keys`() {
        val result =
            resolveActiveTurnSnapshotObserveKey(
                requestedKey = turnKey,
                incompleteKey = turnKey,
                acknowledgedKeys = setOf(turnKey),
                retainedKey = null,
            )

        assertEquals(turnKey, result)
    }

    @Test
    fun `incomplete turn key falls back when acknowledged and no request is active`() {
        val result =
            resolveActiveTurnSnapshotObserveKey(
                requestedKey = null,
                incompleteKey = turnKey,
                acknowledgedKeys = setOf(turnKey),
                retainedKey = null,
            )

        assertEquals(null, result)
    }

    @Test
    fun `maybeAcknowledgeCompletedTurn ignores stale snapshot for new turn`() {
        val previousAssistantId = MessageId("assistant-prev")
        val newAssistantId = MessageId("assistant-new")
        val turnKey = ActiveChatTurnKey(chatId, newAssistantId)
        val acknowledged = mutableListOf<ActiveChatTurnKey>()
        val dbMessages =
            listOf(
                Message(
                    id = previousAssistantId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = Content(text = "done"),
                    messageState = MessageState.COMPLETE,
                ),
                Message(
                    id = newAssistantId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = Content(text = ""),
                    messageState = MessageState.PROCESSING,
                ),
            )
        val staleSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        previousAssistantId to
                            MessageSnapshot(
                                messageId = previousAssistantId,
                                modelType = ModelType.FAST,
                                content = "done",
                                thinkingRaw = "",
                                messageState = MessageState.COMPLETE,
                                isComplete = true,
                            ),
                    ),
            )

        maybeAcknowledgeCompletedTurn(
            key = turnKey,
            dbMessages = dbMessages,
            activeSnapshot = staleSnapshot,
            acknowledgedTurnKeys = emptySet(),
            onAcknowledge = acknowledged::add,
        )

        assertTrue(acknowledged.isEmpty())
    }
}
