package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import com.browntowndev.enclave.domain.port.media.FileAttachmentPort
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessFileAttachmentUseCaseTest {
    private val fileAttachmentPort: FileAttachmentPort = mockk()
    private val cloudDocumentExtractor: CloudDocumentExtractorPort = mockk()
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
            coEvery { fileAttachmentPort.readFileContent(uri) } returns metadata

            val result = useCase(uri)

            assertEquals(metadata, result)
            coVerify(exactly = 1) { fileAttachmentPort.readFileContent(uri) }
            coVerify(exactly = 0) { cloudDocumentExtractor.extractText(any(), any()) }
        }

    @Test
    fun `invoke calls cloud extractor for PDF`() =
        runTest {
            val uri = "content://test.pdf"
            val initialMetadata = FileAttachmentMetadata("test.pdf", "application/pdf", "BASE64:...")
            val extractedMetadata = FileAttachmentMetadata("test.pdf", "application/pdf", "Extracted text")

            coEvery { fileAttachmentPort.readFileContent(uri) } returns initialMetadata
            coEvery { cloudDocumentExtractor.extractText(uri, "application/pdf") } returns extractedMetadata

            val result = useCase(uri)

            assertEquals(extractedMetadata, result)
            coVerify(exactly = 1) { cloudDocumentExtractor.extractText(uri, "application/pdf") }
        }
}
