package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessFileAttachmentUseCaseTest {

    private val fileAttachmentPort: FileAttachmentPort = mockk()
    private lateinit var useCase: ProcessFileAttachmentUseCase

    @BeforeEach
    fun setUp() {
        useCase = ProcessFileAttachmentUseCase(fileAttachmentPort)
    }

    @Test
    fun `invoke calls readFileContent on port`() = runTest {
        val uri = "content://test"
        val metadata = FileAttachmentMetadata("test.txt", "text/plain", "content")
        coEvery { fileAttachmentPort.readFileContent(uri) } returns metadata

        val result = useCase(uri)

        assertEquals(metadata, result)
        coVerify(exactly = 1) { fileAttachmentPort.readFileContent(uri) }
    }
}
