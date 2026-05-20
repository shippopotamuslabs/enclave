package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
import javax.inject.Inject

/**
 * Use case for processing file attachments.
 */
class ProcessFileAttachmentUseCase
    @Inject
    constructor(
        private val fileAttachmentPort: FileAttachmentPort,
        private val cloudDocumentExtractor: CloudDocumentExtractorPort,
    ) {
        /**
         * Reads the file at the given URI and returns its metadata and text content.
         * @param uri The URI of the file to process.
         * @return [FileAttachmentMetadata] containing the file name and content.
         */
        suspend operator fun invoke(uri: String): FileAttachmentMetadata {
            val metadata = fileAttachmentPort.readFileContent(uri)
            val mimeType = metadata.mimeType ?: return metadata

            return if (isCloudExtractionSupported(mimeType)) {
                cloudDocumentExtractor.extractText(uri, mimeType)
            } else {
                metadata
            }
        }

        private fun isCloudExtractionSupported(mimeType: String): Boolean =
            mimeType == "application/pdf" ||
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
