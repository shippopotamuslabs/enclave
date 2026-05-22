package com.browntowndev.enclave.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.enclave.domain.model.inference.ApiProvider

class ApiProviderConverters {
    @TypeConverter
    fun fromApiProvider(provider: ApiProvider): String = provider.name

    @TypeConverter
    fun toApiProvider(name: String): ApiProvider = ApiProvider.valueOf(name)
}
