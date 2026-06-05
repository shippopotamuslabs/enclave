package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.inference.ModelType

data class ModelPack(
    val packId: String,
    val displayName: String,
    val slot: ModelType,
    val members: List<ModelPackMember>,
)

data class ModelPackMember(
    val role: ModelPackRole,
    val asset: LocalModelAsset,
)
