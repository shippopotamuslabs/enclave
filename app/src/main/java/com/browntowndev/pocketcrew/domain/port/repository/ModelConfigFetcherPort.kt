package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.RemoteModelConfig

interface ModelConfigFetcherPort {
    suspend fun fetchRemoteConfig(): Result<List<RemoteModelConfig>>
    fun toModelFiles(configs: List<RemoteModelConfig>): List<ModelFile>
}
