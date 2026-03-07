package com.browntowndev.pocketcrew.domain.port.cache

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration

interface ModelConfigCachePort {
    val fullConfig: List<ModelConfiguration>
    suspend fun initialize()
    fun isInitialized(): Boolean
    fun getMainConfig(): ModelConfiguration?
    fun getVisionConfig(): ModelConfiguration?
    fun getDraftConfig(): ModelConfiguration?
    fun getFastConfig(): ModelConfiguration?
}
