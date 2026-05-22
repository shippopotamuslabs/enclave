package com.browntowndev.enclave.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.enclave.domain.model.inference.PipelineStep

class PipelineStepConverters {
    @TypeConverter
    fun fromPipelineStep(pipelineStep: PipelineStep?): String? = pipelineStep?.name

    @TypeConverter
    fun toPipelineStep(value: String?): PipelineStep? = value?.let { PipelineStep.fromString(it) }
}
