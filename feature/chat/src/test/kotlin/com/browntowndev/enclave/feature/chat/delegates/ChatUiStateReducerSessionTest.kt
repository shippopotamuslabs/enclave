package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.chat.ArtifactExportSheetState
import com.browntowndev.enclave.feature.chat.ChatMessageProjector
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.ShareState
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChatUiStateReducer session projector clear")
class ChatUiStateReducerSessionTest {
    private val reducer = ChatUiStateReducer(ChatMessageUiMapper())

    @Test
    @DisplayName("SC-PERF-08 — reducer calls projector onActiveChatChanged on chat change")
    fun scPerf08_reducerClearsProjectorOnChatChange() {
        val messages =
            listOf(
                Message(
                    id = MessageId("msg-1"),
                    chatId = ChatId("chat-1"),
                    content = Content("hello"),
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                    createdAt = 1L,
                ),
            )

        reduce(messages, ChatId("chat-1"))
        reduce(messages, ChatId("chat-2"))

        assertEquals(ChatId("chat-2"), reducer.projectorCachedActiveChatId())
    }

    private fun ChatUiStateReducer.projectorCachedActiveChatId(): ChatId? {
        val reducerField = ChatUiStateReducer::class.java.getDeclaredField("chatMessageProjector")
        reducerField.isAccessible = true
        val projector = reducerField.get(this) as ChatMessageProjector
        val cachedField = ChatMessageProjector::class.java.getDeclaredField("cachedActiveChatId")
        cachedField.isAccessible = true
        return when (val raw = cachedField.get(projector)) {
            null -> null
            is ChatId -> raw
            is String -> ChatId(raw)
            else -> error("Unexpected cachedActiveChatId type: ${raw::class.java}")
        }
    }

    private fun reduce(
        dbMessages: List<Message>,
        chatId: ChatId,
    ) = reducer.reduce(
        ChatUiStateReducer.ReduceParams(
            settings = SettingsData(),
            inputText = "",
            selectedMode = ChatModeUi.FAST,
            selectedImageUris = emptyList(),
            selectedFileName = null,
            speechState = SpeechState.Idle,
            attachmentPolicy =
                PhotoAttachmentPolicy(
                    isEnabled = false,
                    maxItems = 0,
                    disabledReason = null,
                ),
            isGlobalInferenceBlocked = false,
            dbMessages = dbMessages,
            activeTurnKey = null,
            activeTurnSnapshot = null,
            activeToolCallBanner = null,
            hasTtsProviderAssigned = false,
            isPlayingTts = false,
            studioMediaMap = emptyMap(),
            shareState = ShareState.Idle,
            isReadOnly = false,
            sharedMessages = emptyList(),
            artifactExportSheet = ArtifactExportSheetState.Hidden,
            chatId = chatId,
            modelDisplayNames = emptyMap(),
        ),
    )
}
