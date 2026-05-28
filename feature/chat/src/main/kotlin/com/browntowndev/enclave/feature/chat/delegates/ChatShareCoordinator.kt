package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.share.ShareOption
import com.browntowndev.enclave.domain.usecase.share.ImportSharedChatUseCase
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.chat.ShareState
import com.browntowndev.enclave.feature.chat.ShareUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatShareCoordinator
    @Inject
    constructor(
        private val shareChatUseCase: ShareChatUseCase,
        private val importSharedChatUseCase: ImportSharedChatUseCase,
    ) {
        private companion object {
            const val DEFAULT_SHARE_ERROR_MESSAGE = "Couldn't share this conversation. Please try again."
        }

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
                        _shareState.value =
                            ShareState.Error(
                                error.message ?: DEFAULT_SHARE_ERROR_MESSAGE,
                            )
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
                        _shareState.value =
                            ShareState.Error(
                                error.message ?: DEFAULT_SHARE_ERROR_MESSAGE,
                            )
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
                        if (sharedChat != null) {
                            val chatId = importSharedChatUseCase(sharedChat)
                            _shareUiEvents.emit(ShareUiEvent.ImportedChat(chatId))
                        }
                    }.onFailure { error ->
                        _shareState.value =
                            ShareState.Error(
                                error.message ?: DEFAULT_SHARE_ERROR_MESSAGE,
                            )
                    }
            }
        }
    }
