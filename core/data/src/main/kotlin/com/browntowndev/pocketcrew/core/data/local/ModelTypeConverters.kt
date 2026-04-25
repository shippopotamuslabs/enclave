package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import java.util.Date

/**
 * Type converters for Room to convert complex types to/from database-friendly formats.
 */
class ModelTypeConverters {
    @TypeConverter
    fun fromModelType(modelType: ModelType): String = modelType.apiValue

    @TypeConverter
    fun toModelType(value: String): ModelType = ModelType.fromApiValue(value)

    @TypeConverter
    fun fromModelFileFormat(modelFileFormat: ModelFileFormat): String = modelFileFormat.name

    @TypeConverter
    fun toModelFileFormat(value: String): ModelFileFormat = ModelFileFormat.valueOf(value)

    @TypeConverter
    fun fromLocalModelConfigurationId(id: LocalModelConfigurationId): String = id.value

    @TypeConverter
    fun toLocalModelConfigurationId(value: String): LocalModelConfigurationId = LocalModelConfigurationId(value)

    @TypeConverter
    fun fromApiModelConfigurationId(id: ApiModelConfigurationId): String = id.value

    @TypeConverter
    fun toApiModelConfigurationId(value: String): ApiModelConfigurationId = ApiModelConfigurationId(value)

    @TypeConverter
    fun fromTtsProviderId(id: TtsProviderId): String = id.value

    @TypeConverter
    fun toTtsProviderId(value: String): TtsProviderId = TtsProviderId(value)

    @TypeConverter
    fun fromChatId(id: ChatId): String = id.value

    @TypeConverter
    fun toChatId(value: String): ChatId = ChatId(value)

    @TypeConverter
    fun fromMessageId(id: MessageId): String = id.value

    @TypeConverter
    fun toMessageId(value: String): MessageId = MessageId(value)

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }
}
