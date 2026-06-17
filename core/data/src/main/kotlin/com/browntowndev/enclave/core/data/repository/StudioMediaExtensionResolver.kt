package com.browntowndev.enclave.core.data.repository

internal object StudioMediaExtensionResolver {
    fun extensionFor(mediaType: String): String =
        when (mediaType) {
            "VIDEO" -> "mp4"
            "MUSIC" -> "mp3"
            else -> "png"
        }
}
