package com.browntowndev.pocketcrew.domain.port.repository

interface ModelConfigProvider {
    /**
     * The fully resolved directory path where models are stored.
     */
    val modelsDirectoryPath: String
}
