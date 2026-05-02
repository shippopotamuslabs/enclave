package com.browntowndev.pocketcrew.domain.model.inference

data class ToolExecutionResult(
    val toolName: String,
    val resultJson: String,
    val cached: Boolean = false,
    val latencyMs: Long = 0,
)
