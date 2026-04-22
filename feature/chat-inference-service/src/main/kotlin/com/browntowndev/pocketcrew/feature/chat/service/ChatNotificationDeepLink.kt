package com.browntowndev.pocketcrew.feature.chat.service

import com.browntowndev.pocketcrew.domain.model.chat.ChatId

object ChatNotificationDeepLink {
    const val URI_PATTERN = "pocketcrew://chat/{chatId}"

    fun uriStringFor(chatId: ChatId): String = "pocketcrew://chat/${chatId.value}"
}
