package com.browntowndev.pocketcrew.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.MessageState

/**
 * Type converters for Room to convert MessageState enum to/from String.
 */
class MessageStateConverters {
    @TypeConverter
    fun fromMessageState(messageState: MessageState): String = messageState.name

    @TypeConverter
    fun toMessageState(value: String): MessageState = MessageState.valueOf(value)
}
