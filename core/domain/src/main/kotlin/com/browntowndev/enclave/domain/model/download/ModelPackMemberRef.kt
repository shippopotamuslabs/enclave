package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.UtilityType

data class ModelPackMemberRef(
    val role: ModelPackRole,
    val fileName: String,
    val utilityType: UtilityType? = null,
)
