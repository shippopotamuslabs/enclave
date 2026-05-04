package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
import javax.inject.Inject

/**
 * Use case for processing file attachments.
 */
class ProcessFileAttachmentUseCase @Inject constructor(
    private val fileAttachmentPort: FileAttachmentPort
) {
    /**
     * Reads the file at the given URI and returns its metadata and text content.
     * @param uri The URI of the file to process.
     * @return [FileAttachmentMetadata] containing the file name and content.
     */
    suspend operator fun invoke(uri: String): FileAttachmentMetadata {
        return fileAttachmentPort.readFileContent(uri)
    }
}
