package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.Serializable

@Serializable
data class GenerateMusicToolParams(
    @property:ToolParam(description = "A detailed description of the music to generate, including genre, mood, tempo, instruments.")
    val prompt: String,
    @property:ToolParam(description = "Duration of the music in seconds.", required = false)
    val duration: Int = 30,
) : ToolParameters
