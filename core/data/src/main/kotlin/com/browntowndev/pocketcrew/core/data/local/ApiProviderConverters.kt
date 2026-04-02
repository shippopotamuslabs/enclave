package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

class ApiProviderConverters {
    @TypeConverter
    fun fromApiProvider(provider: ApiProvider): String = provider.name

    @TypeConverter
    fun toApiProvider(name: String): ApiProvider = ApiProvider.valueOf(name)
}