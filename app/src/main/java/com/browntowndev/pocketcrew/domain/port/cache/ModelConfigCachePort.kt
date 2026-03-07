package com.browntowndev.pocketcrew.domain.port.cache

import com.browntowndev.pocketcrew.domain.port.repository.RegisteredModel

interface ModelConfigCachePort {
    val fullConfig: List<RegisteredModel>
    suspend fun initialize()
    fun isInitialized(): Boolean
    fun getMainConfig(): RegisteredModel?
    fun getVisionConfig(): RegisteredModel?
    fun getDraftConfig(): RegisteredModel?
    fun getFastConfig(): RegisteredModel?
}
