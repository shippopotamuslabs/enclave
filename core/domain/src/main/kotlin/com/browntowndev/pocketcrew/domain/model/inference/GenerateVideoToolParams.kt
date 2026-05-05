package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable

@Serializable
data class GenerateVideoToolParams(
    @property:ToolParam(description = "A detailed description of the video to generate.")
    val prompt: String,
    @property:ToolParam(description = "Aspect ratio for the generated video.", required = false)
    val aspect_ratio: String = "1:1",
    @property:ToolParam(description = "Quality preset for generation.", required = false)
    val quality: String = "speed",
    @property:ToolParam(description = "Video duration in seconds.", required = false)
    val duration: Int = 6,
    @property:ToolParam(description = "Video resolution.", required = false)
    val resolution: String = "720p",
) : ToolParameters
