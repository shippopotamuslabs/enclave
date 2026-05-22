package com.browntowndev.enclave.feature.chat.service

import com.browntowndev.enclave.domain.model.chat.ChatId

object ChatNotificationDeepLink {
    const val URI_PATTERN = "enclave://chat/{chatId}"

    fun uriStringFor(chatId: ChatId): String = "enclave://chat/${chatId.value}"
}
