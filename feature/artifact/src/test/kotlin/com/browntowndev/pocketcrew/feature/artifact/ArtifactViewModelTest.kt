package com.browntowndev.pocketcrew.feature.artifact

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.data.artifact.PdfArtifactRenderer
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactBlock
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactSection
import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLEncoder

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pdfRenderer: PdfArtifactRenderer = mockk()
    private val shareMediaPort: ShareMediaPort = mockk(relaxed = true)
    
    private val testTitle = "Test Artifact"
    private val testSections = listOf(
        ArtifactSection(
            title = "Section 1",
            blocks = listOf(ArtifactBlock.Paragraph("Hello World"))
        )
    )
    private val testRequest = ArtifactGenerationRequest(
        documentType = DocumentType.PDF,
        title = testTitle,
        sections = testSections
    )
    private val jsonContent = Json.encodeToString(testSections)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should decode URL-encoded content and title`() = runTest {
        val encodedTitle = URLEncoder.encode(testTitle, "UTF-8")
        val encodedContent = URLEncoder.encode(jsonContent, "UTF-8")
        
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "title" to encodedTitle,
                "content" to encodedContent
            )
        )

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.result)
        assertEquals(testTitle, uiState.result?.request?.title)
        assertEquals(1, uiState.result?.request?.sections?.size)
        assertEquals("Section 1", uiState.result?.request?.sections?.first()?.title)
    }

    @Test
    fun `exportToPdf should call renderer and share port`() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "title" to testTitle,
                "content" to jsonContent
            )
        )
        
        val mockFile = mockk<File>()
        every { mockFile.absolutePath } returns "/path/to/test.pdf"
        coEvery { pdfRenderer.renderToPdf(any(), any()) } returns mockFile

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()

        viewModel.exportToPdf()
        advanceUntilIdle()

        // Test title is "Test Artifact", so safe name is "Test_Artifact.pdf"
        coVerify { pdfRenderer.renderToPdf(any(), "Test_Artifact.pdf") }
        coVerify { shareMediaPort.shareMedia(listOf("/path/to/test.pdf"), "application/pdf") }
    }

    @Test
    fun `init should decode content with SerialNames`() = runTest {
        val jsonWithSerialNames = """
            [
              {
                "title": "Section 1",
                "blocks": [
                  {
                    "type": "paragraph",
                    "text": "Hello World"
                  },
                  {
                    "type": "heading",
                    "level": 1,
                    "text": "My Heading"
                  },
                  {
                    "type": "bullet_list",
                    "items": ["Item 1"]
                  },
                  {
                    "type": "code_block",
                    "language": "kotlin",
                    "code": "val x = 1"
                  }
                ]
              }
            ]
        """.trimIndent()
        
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "title" to "Test",
                "content" to jsonWithSerialNames
            )
        )

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.result)
        assertEquals(1, uiState.result?.request?.sections?.size)
        val section = uiState.result?.request?.sections?.first()
        assertEquals(4, section?.blocks?.size)
        assertTrue(section?.blocks?.get(0) is ArtifactBlock.Paragraph)
        assertTrue(section?.blocks?.get(1) is ArtifactBlock.Heading)
        assertTrue(section?.blocks?.get(2) is ArtifactBlock.BulletList)
        assertTrue(section?.blocks?.get(3) is ArtifactBlock.CodeBlock)
    }
}
