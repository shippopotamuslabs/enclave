package com.browntowndev.pocketcrew.domain.model.chat

@JvmInline
value class ChatId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChatId cannot be blank" }
    }
}
