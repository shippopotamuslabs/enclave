package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.PipelineState

/**
 * Port for serializing and deserializing pipeline state for WorkManager.
 */
interface PipelineStateSerializer {
    fun toJson(state: PipelineState): String
    fun fromJson(json: String): PipelineState
}
