package com.browntowndev.enclave.domain.model.profile

data class ProfileAnalysisProgress(
    val currentPass: Int,
    val totalPasses: Int,
    val isRunning: Boolean,
    val isComplete: Boolean,
    val error: String?,
)
