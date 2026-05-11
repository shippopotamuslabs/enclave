package com.browntowndev.pocketcrew.core.docgen

import com.browntowndev.pocketcrew.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
import javax.inject.Inject

class LocalDocumentExtractorAdapter @Inject constructor(
    private val fileAttachmentPort: FileAttachmentPort
) : CloudDocumentExtractorPort {
    override suspend fun extractText(uri: String, mimeType: String): FileAttachmentMetadata {
        val metadata = fileAttachmentPort.readFileContent(uri)
        val bytes = fileAttachmentPort.readBytes(uri)
        
        return try {
            // Explicitly call the top-level UniFFI function
            val text = com.browntowndev.pocketcrew.core.docgen.extractText(bytes, mimeType)
            metadata.copy(content = text)
        } catch (e: Exception) {
            metadata.copy(content = "Error extracting text: ${e.message}")
        }
    }
}
