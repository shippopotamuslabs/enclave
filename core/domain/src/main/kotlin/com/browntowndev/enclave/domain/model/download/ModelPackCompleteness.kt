package com.browntowndev.enclave.domain.model.download

data class ModelPackMemberStatus(
    val role: ModelPackRole,
    val fileName: String,
    val isVerifiedOnDisk: Boolean,
)

data class ModelPackCompleteness(
    val packId: String,
    val installedCount: Int,
    val totalCount: Int,
    val isComplete: Boolean,
    val members: List<ModelPackMemberStatus>,
)
