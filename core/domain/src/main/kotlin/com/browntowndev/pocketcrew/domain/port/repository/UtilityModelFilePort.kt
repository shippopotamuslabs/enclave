package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.UtilityType

interface UtilityModelFilePort {
    suspend fun resolveUtilityModelPath(utilityType: UtilityType): String?
}
