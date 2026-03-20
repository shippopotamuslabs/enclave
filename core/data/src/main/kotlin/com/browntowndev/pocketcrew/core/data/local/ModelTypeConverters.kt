package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Type converters for Room to convert ModelType and ModelFileFormat enums to/from String.
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
}
