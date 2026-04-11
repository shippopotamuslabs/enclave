package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId

class IdTypeConverters {
    @TypeConverter
    fun fromChatId(id: ChatId): String = id.value

    @TypeConverter
    fun toChatId(value: String): ChatId = ChatId(value)

    @TypeConverter
    fun fromMessageId(id: MessageId): String = id.value

    @TypeConverter
    fun toMessageId(value: String): MessageId = MessageId(value)

    @TypeConverter
    fun fromLocalModelId(id: LocalModelId): String = id.value

    @TypeConverter
    fun toLocalModelId(value: String): LocalModelId = LocalModelId(value)

    @TypeConverter
    fun fromApiCredentialsId(id: ApiCredentialsId): String = id.value

    @TypeConverter
    fun toApiCredentialsId(value: String): ApiCredentialsId = ApiCredentialsId(value)
}
