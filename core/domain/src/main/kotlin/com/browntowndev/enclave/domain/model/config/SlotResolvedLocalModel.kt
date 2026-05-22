package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelType

/**
 * Explicitly pairs a ModelType slot with its selected configuration and the underlying asset.
 * This prevents ambiguity when a single asset has multiple configurations attached.
 */
data class SlotResolvedLocalModel(
    val modelType: ModelType,
    val asset: LocalModelAsset,
    val selectedConfig: LocalModelConfiguration,
)
