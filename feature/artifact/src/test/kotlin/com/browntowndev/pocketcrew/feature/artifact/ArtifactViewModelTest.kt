package com.browntowndev.pocketcrew.feature.artifact

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.data.artifact.PdfArtifactRenderer
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    private val testMarkdown = "# Section 1\nHello World"
    private val testRequest = ArtifactGenerationRequest(
        documentType = DocumentType.PDF,
        title = testTitle,
        content = testMarkdown
    )

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
        val encodedContent = URLEncoder.encode(testMarkdown, "UTF-8")
        
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
        assertEquals(testMarkdown, uiState.result?.request?.content)
    }

    @Test
    fun `exportToPdf should call renderer and share port`() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "title" to testTitle,
                "content" to testMarkdown
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
    fun `init should handle raw markdown content fallback`() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "title" to "Test",
                "content" to testMarkdown
            )
        )

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.result)
        assertEquals(testMarkdown, uiState.result?.request?.content)
    }
}
