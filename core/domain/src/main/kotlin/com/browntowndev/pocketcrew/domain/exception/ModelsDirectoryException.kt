package com.browntowndev.pocketcrew.domain.exception

/**
 * Domain exception thrown when models directory creation fails.
 * 
 * This exception is thrown when the file scanner fails to create the models
 * directory, which is required for storing downloaded model files.
 * 
 * REF: CLARIFIED_REQUIREMENTS.md - Section 8 (CheckModelsUseCase - Directory Error)
 */
class ModelsDirectoryException(
    message: String = "Failed to create models directory"
) : Exception(message)
