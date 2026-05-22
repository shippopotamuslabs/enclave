package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.Serializable

@Serializable
data class GenerateImageToolParams(
    @property:ToolParam(description = "A detailed description of the image to generate.")
    val prompt: String,
    @property:ToolParam(description = "Aspect ratio for the generated image.", required = false)
    val aspect_ratio: String = "1:1",
    @property:ToolParam(description = "Quality preset for generation.", required = false)
    val quality: String = "speed",
    @property:ToolParam(description = "Number of images to generate.", required = false)
    val count: Int = 1,
) : ToolParameters
