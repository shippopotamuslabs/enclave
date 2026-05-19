package com.browntowndev.pocketcrew.feature.chat.delegates

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.share.ShareOption
import com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase
import com.browntowndev.pocketcrew.feature.chat.ShareState
import com.browntowndev.pocketcrew.feature.chat.ShareUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ChatShareCoordinator
    @Inject
    constructor(
        private val shareChatUseCase: ShareChatUseCase,
    ) {
        private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
        val shareState: StateFlow<ShareState> = _shareState.asStateFlow()

        private val _shareUiEvents = MutableSharedFlow<ShareUiEvent>(extraBufferCapacity = 1)
        val shareUiEvents: SharedFlow<ShareUiEvent> = _shareUiEvents.asSharedFlow()

        private val _isReadOnly = MutableStateFlow(false)
        val isReadOnly: StateFlow<Boolean> = _isReadOnly.asStateFlow()

        private val _sharedMessages = MutableStateFlow<List<Message>>(emptyList())
        val sharedMessages: StateFlow<List<Message>> = _sharedMessages.asStateFlow()

        private val shareUrlCache = mutableMapOf<ShareOption, String>()
        private var pendingShareMessageId: MessageId? = null

        fun onShareClick(
            messageId: MessageId,
            chatId: ChatId?,
        ) {
            pendingShareMessageId = messageId
            val resolvedChatId = chatId ?: return
            val option = ShareOption.SingleTurn(resolvedChatId, messageId)
            val cachedUrl = shareUrlCache[option]
            if (cachedUrl != null) {
                _shareState.value = ShareState.Success(cachedUrl)
            } else {
                _shareState.value = ShareState.ShowingSheet
            }
        }

        fun onShareTurn(
            scope: CoroutineScope,
            chatId: ChatId?,
        ) {
            val resolvedChatId = chatId ?: return
            val messageId = pendingShareMessageId ?: return
            val option = ShareOption.SingleTurn(resolvedChatId, messageId)
            scope.launch {
                _shareState.value = ShareState.Loading
                shareChatUseCase(option)
                    .onSuccess { sharedChat ->
                        shareUrlCache[option] = sharedChat.shareUrl
                        _shareState.value = ShareState.Success(sharedChat.shareUrl)
                        _shareUiEvents.emit(ShareUiEvent.OpenShareSheet(sharedChat.shareUrl))
                    }.onFailure { error ->
                        _shareState.value = ShareState.Error(error.message ?: "Failed to share chat")
                    }
            }
        }

        fun onShareFullChat(
            scope: CoroutineScope,
            chatId: ChatId?,
        ) {
            val resolvedChatId = chatId ?: return
            val option = ShareOption.FullConversation(resolvedChatId)
            scope.launch {
                val cachedUrl = shareUrlCache[option]
                if (cachedUrl != null) {
                    _shareState.value = ShareState.Success(cachedUrl)
                    _shareUiEvents.emit(ShareUiEvent.OpenShareSheet(cachedUrl))
                    return@launch
                }
                _shareState.value = ShareState.Loading
                shareChatUseCase(option)
                    .onSuccess { sharedChat ->
                        shareUrlCache[option] = sharedChat.shareUrl
                        _shareState.value = ShareState.Success(sharedChat.shareUrl)
                        _shareUiEvents.emit(ShareUiEvent.OpenShareSheet(sharedChat.shareUrl))
                    }.onFailure { error ->
                        _shareState.value = ShareState.Error(error.message ?: "Failed to share chat")
                    }
            }
        }

        fun onDismissShareSheet() {
            pendingShareMessageId = null
            _shareState.value = ShareState.Idle
        }

        fun onLoadSharedChat(
            scope: CoroutineScope,
            shareId: String,
            decryptionKey: String? = null,
        ) {
            scope.launch {
                shareChatUseCase
                    .getSharedChat(shareId, decryptionKey)
                    .onSuccess { sharedChat ->
                        sharedChat?.let { chat ->
                            val mappedMessages =
                                withContext(Dispatchers.Default) {
                                    chat.messages.mapIndexed { index, sharedMessage ->
                                        Message(
                                            id = MessageId("shared-$index"),
                                            chatId = ChatId("shared-chat"),
                                            role = Role.fromApiValue(sharedMessage.role),
                                            content = Content(text = sharedMessage.content),
                                            createdAt = sharedMessage.timestamp,
                                            messageState = MessageState.COMPLETE,
                                        )
                                    }
                                }
                            _sharedMessages.value = mappedMessages
                            _isReadOnly.value = true
                            _shareState.value = ShareState.Idle
                        }
                    }
            }
        }
    }
