package com.browntowndev.enclave.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.LocalModelId

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
