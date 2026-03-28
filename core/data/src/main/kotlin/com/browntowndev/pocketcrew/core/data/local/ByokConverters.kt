package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource

class ApiProviderConverters {
    @TypeConverter
    fun fromApiProvider(provider: ApiProvider): String = provider.name

    @TypeConverter
    fun toApiProvider(value: String): ApiProvider = ApiProvider.valueOf(value)
}

class ModelSourceConverters {
    @TypeConverter
    fun fromModelSource(source: ModelSource): String = source.name

    @TypeConverter
    fun toModelSource(value: String): ModelSource = ModelSource.valueOf(value)
}
