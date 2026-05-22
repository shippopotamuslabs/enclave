package com.browntowndev.enclave.domain.model.inference

/**
 * Shared constants for Google GenAI API integration.
 */
object GoogleApiConstants {
    /**
     * The default API version to use for Google GenAI requests.
     * v1beta is preferred for newer models like Imagen 3 and preview features.
     */
    const val DEFAULT_API_VERSION = "v1beta"
}
