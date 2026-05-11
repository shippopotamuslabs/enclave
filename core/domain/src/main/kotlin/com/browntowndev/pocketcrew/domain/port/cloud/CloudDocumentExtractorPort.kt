package com.browntowndev.pocketcrew.domain.port.cloud

import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata

/**
 * Port for extracting text from complex document formats (PDF, DOCX, XLSX) via cloud services.
 */
interface CloudDocumentExtractorPort {
    /**
     * Extracts text from a document using cloud services.
     * @param uri The URI of the file to extract from.
     * @param mimeType The MIME type of the file.
     * @return [FileAttachmentMetadata] with extracted text.
     * @throws Exception if extraction fails.
     */
    suspend fun extractText(uri: String, mimeType: String): FileAttachmentMetadata
}
