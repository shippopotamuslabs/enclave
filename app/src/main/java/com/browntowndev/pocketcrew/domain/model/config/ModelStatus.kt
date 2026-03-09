package com.browntowndev.pocketcrew.domain.model.config

/**
 * Enum representing the status of a model in the registry.
 */
enum class ModelStatus {
    /**
     * The model is the current version and should be used.
     */
    CURRENT,

    /**
     * The model is an old version that has been replaced.
     * Can be cleaned up after a successful download.
     */
    OLD
}
