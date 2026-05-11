package com.browntowndev.pocketcrew.domain.model.media

/**
 * A generated document to share via [com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort.shareDocumentBytes].
 * Implementations stage bytes to cache and grant read URIs (e.g. FileProvider).
 */
data class ShareDocumentBytesInput(
    val suggestedFileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShareDocumentBytesInput

        if (suggestedFileName != other.suggestedFileName) return false
        if (mimeType != other.mimeType) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = suggestedFileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
