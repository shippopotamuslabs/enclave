package com.browntowndev.pocketcrew.domain.model.error

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Thrown when a default model assignment exists but encrypted configuration fields cannot be
 * decrypted with the active vault key (wrong DEK, locked vault, or corrupted ciphertext).
 */
class ActiveModelConfigurationDecryptException(
    val modelType: ModelType,
    message: String = USER_MESSAGE,
) : Exception(message) {
    companion object {
        const val USER_MESSAGE =
            "Could not read your model settings. Unlock your vault with your password and try again."
    }
}
