package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeCloudDocumentExtractor
import com.browntowndev.enclave.core.testing.FakeFileAttachmentPort
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessFileAttachmentUseCaseTest {
    private val fileAttachmentPort = FakeFileAttachmentPort()
    private val cloudDocumentExtractor = FakeCloudDocumentExtractor()
    private lateinit var useCase: ProcessFileAttachmentUseCase

    @BeforeEach
    fun setUp() {
        useCase = ProcessFileAttachmentUseCase(fileAttachmentPort, cloudDocumentExtractor)
    }

    @Test
    fun `invoke calls readFileContent on port for plain text`() =
        runTest {
            val uri = "content://test"
            val metadata = FileAttachmentMetadata("test.txt", "text/plain", "content")
            fileAttachmentPort.metadataByUri[uri] = metadata

            val result = useCase(uri)

            assertEquals(metadata, result)
            assertEquals(listOf(uri), fileAttachmentPort.readFileContentCalls)
            assertTrue(cloudDocumentExtractor.extractCalls.isEmpty())
        }

    @Test
    fun `invoke calls cloud extractor for PDF`() =
        runTest {
            val uri = "content://test.pdf"
            val initialMetadata = FileAttachmentMetadata("test.pdf", "application/pdf", "BASE64:...")
            val extractedMetadata = FileAttachmentMetadata("test.pdf", "application/pdf", "Extracted text")

            fileAttachmentPort.metadataByUri[uri] = initialMetadata
            cloudDocumentExtractor.extractedByCall[FakeCloudDocumentExtractor.ExtractCall(uri, "application/pdf")] =
                extractedMetadata

            val result = useCase(uri)

            assertEquals(extractedMetadata, result)
            assertEquals(
                listOf(FakeCloudDocumentExtractor.ExtractCall(uri, "application/pdf")),
                cloudDocumentExtractor.extractCalls,
            )
        }
}
