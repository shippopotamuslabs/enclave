package com.browntowndev.pocketcrew.domain.port.repository

import java.io.File

/**
 * Port (interface) for model configuration.
 * Implemented by the data layer.
 *
 * Provides access to configuration constants and the models directory,
 * enabling domain layer to remain independent of data layer specifics.
 */
interface ModelConfigProvider {
    /**
     * The fully resolved directory where models are stored.
     * This is a File object representing the complete path.
     */
    val modelsDirectory: File
}
