package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import com.browntowndev.enclave.domain.port.media.FileAttachmentPort

/** Recording [FileAttachmentPort] fake (ENC-28): per-URI canned metadata/bytes. */
class FakeFileAttachmentPort : FileAttachmentPort {
    val readFileContentCalls = mutableListOf<String>()
    val readBytesCalls = mutableListOf<String>()

    var metadataByUri = mutableMapOf<String, FileAttachmentMetadata>()
    var bytesByUri = mutableMapOf<String, ByteArray>()

    override suspend fun readFileContent(uri: String): FileAttachmentMetadata {
        readFileContentCalls += uri
        return metadataByUri[uri] ?: error("FakeFileAttachmentPort: no metadata stubbed for $uri")
    }

    override suspend fun readBytes(uri: String): ByteArray {
        readBytesCalls += uri
        return bytesByUri[uri] ?: ByteArray(0)
    }
}

/** Recording [CloudDocumentExtractorPort] fake (ENC-28): per-(uri, mimeType) canned metadata. */
class FakeCloudDocumentExtractor : CloudDocumentExtractorPort {
    data class ExtractCall(
        val uri: String,
        val mimeType: String,
    )

    val extractCalls = mutableListOf<ExtractCall>()

    var extractedByCall = mutableMapOf<ExtractCall, FileAttachmentMetadata>()

    override suspend fun extractText(
        uri: String,
        mimeType: String,
    ): FileAttachmentMetadata {
        val call = ExtractCall(uri, mimeType)
        extractCalls += call
        return extractedByCall[call] ?: error("FakeCloudDocumentExtractor: no metadata stubbed for $call")
    }
}
