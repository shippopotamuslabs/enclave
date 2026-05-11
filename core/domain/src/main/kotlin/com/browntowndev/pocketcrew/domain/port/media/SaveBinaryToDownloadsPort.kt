package com.browntowndev.pocketcrew.domain.port.media

/**
 * Writes raw bytes into the user's **public Downloads** area (e.g. MediaStore).
 */
fun interface SaveBinaryToDownloadsPort {
    suspend fun save(bytes: ByteArray, displayName: String, mimeType: String): Result<Unit>
}
