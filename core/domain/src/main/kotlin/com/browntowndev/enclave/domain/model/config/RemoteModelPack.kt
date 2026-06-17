package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.serialization.Serializable

@Serializable
data class RemoteModelPackMember(
    val role: String,
    val fileName: String,
    val utilityType: UtilityType? = null,
)

@Serializable
data class RemoteModelPack(
    val packId: String,
    val displayName: String,
    val slot: ModelType,
    val members: List<RemoteModelPackMember>,
)
