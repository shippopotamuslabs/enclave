package com.browntowndev.pocketcrew.domain.model.inference

import java.util.Base64

/**
 * A provider-independent representation of an image payload.
 *
 * @property bytes The raw original bytes of the image.
 * @property mimeType The authoritative MIME type of the image.
 * @property byteCount The total number of bytes.
 * @property sourceUri The source URI from which the image was loaded.
 * @property filename The original filename of the image.
 * @property sha256 The SHA-256 hash of the original bytes.
 */
data class ImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val byteCount: Long,
    val sourceUri: String,
    val filename: String,
    val sha256: String,
) {
    val base64: String
        get() = Base64.getEncoder().encodeToString(bytes)

    val dataUrl: String
        get() = "data:$mimeType;base64,$base64"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImagePayload

        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false
        if (byteCount != other.byteCount) return false
        if (sourceUri != other.sourceUri) return false
        if (filename != other.filename) return false
        if (sha256 != other.sha256) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + byteCount.hashCode()
        result = 31 * result + sourceUri.hashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + sha256.hashCode()
        return result
    }
}
