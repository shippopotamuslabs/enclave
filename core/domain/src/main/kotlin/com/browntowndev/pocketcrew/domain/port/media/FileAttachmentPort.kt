package com.browntowndev.pocketcrew.domain.port.media

/**
 * Represents metadata and extracted content from an attached file.
 */
data class FileAttachmentMetadata(
    val name: String,
    val mimeType: String?,
    val content: String
)

/**
 * Port for reading file attachments from various URI schemes.
 */
interface FileAttachmentPort {
    /**
     * Reads the file at the given URI and returns its metadata and text content.
     * @throws NotImplementedError if the MIME type is not supported.
     */
    suspend fun readFileContent(uri: String): FileAttachmentMetadata
}
