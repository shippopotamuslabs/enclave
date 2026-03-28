package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Identifies whether a model slot is backed by an on-device engine or an API.
 */
enum class ModelSource {
    ON_DEVICE,
    API,
}
