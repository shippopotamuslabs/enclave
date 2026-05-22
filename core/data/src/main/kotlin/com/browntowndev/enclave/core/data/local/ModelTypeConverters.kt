package com.browntowndev.enclave.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.enclave.domain.model.artifact.FileSource
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import java.util.Date

/**
 * Type converters for Room to convert complex types to/from database-friendly formats.
 */
class ModelTypeConverters {
    @TypeConverter
    fun fromMimeType(type: MimeType?): String? = type?.name

    @TypeConverter
    fun toMimeType(value: String?): MimeType? = value?.let { MimeType.valueOf(it) }

    @TypeConverter
    fun fromFileSource(source: FileSource): String = source.name

    @TypeConverter
    fun toFileSource(value: String): FileSource = FileSource.valueOf(value)

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
    fun fromMemoryCategory(category: MemoryCategory): String = category.name

    @TypeConverter
    fun toMemoryCategory(value: String): MemoryCategory = MemoryCategory.valueOf(value)

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }
}
