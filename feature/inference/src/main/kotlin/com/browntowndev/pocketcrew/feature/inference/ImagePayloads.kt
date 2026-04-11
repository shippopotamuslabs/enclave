package com.browntowndev.pocketcrew.feature.inference

import java.io.File
import java.net.URI
import java.net.URLConnection
import java.util.Base64

internal data class ImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
) {
    val base64: String
        get() = Base64.getEncoder().encodeToString(bytes)

    val dataUrl: String
        get() = "data:$mimeType;base64,$base64"
}

internal object ImagePayloads {
    fun fromUris(imageUris: List<String>): List<ImagePayload> =
        imageUris.map(::fromUri)

    private fun fromUri(imageUri: String): ImagePayload {
        val file = File(URI(imageUri))
        require(file.exists()) { "Image file does not exist: $imageUri" }
        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "image/jpeg"
        return ImagePayload(
            bytes = file.readBytes(),
            mimeType = mimeType,
        )
    }
}
