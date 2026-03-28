package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Represents which model (on-device or API) is assigned to a given ModelType slot.
 */
data class DefaultModelAssignment(
    val modelType: ModelType,
    val source: ModelSource,
    val apiModelConfig: ApiModelConfig? = null,
    val onDeviceDisplayName: String? = null,
)
