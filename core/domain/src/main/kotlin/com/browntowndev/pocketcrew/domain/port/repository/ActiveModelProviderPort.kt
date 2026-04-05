package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

interface ActiveModelProviderPort {
    suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration?
}
