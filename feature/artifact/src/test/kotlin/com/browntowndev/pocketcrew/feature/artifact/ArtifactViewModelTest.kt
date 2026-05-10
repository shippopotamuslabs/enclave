package com.browntowndev.pocketcrew.feature.artifact

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.data.artifact.PdfArtifactRenderer
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLEncoder

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactViewModelTest {

    private val pdfRenderer = mockk<PdfArtifactRenderer>(relaxed = true)
    private val shareMediaPort = mockk<ShareMediaPort>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val artifactRequest = ArtifactGenerationRequest(
        targetFormat = MimeType.PDF,
        title = "Test Artifact",
        content = "# Markdown Content"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `parses encoded markdown content correctly`() = runTest {
        val encodedContent = URLEncoder.encode(artifactRequest.content, "UTF-8")
        val savedStateHandle = SavedStateHandle(mapOf(
            "title" to artifactRequest.title,
            "content" to encodedContent
        ))

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.result)
        assertEquals(artifactRequest.title, uiState.result?.request?.title)
        assertEquals(artifactRequest.content, uiState.result?.request?.content)
    }

    @Test
    fun `parses encoded JSON request correctly`() = runTest {
        val jsonRequest = Json.encodeToString(artifactRequest)
        val encodedContent = URLEncoder.encode(jsonRequest, "UTF-8")
        val savedStateHandle = SavedStateHandle(mapOf(
            "title" to artifactRequest.title,
            "content" to encodedContent
        ))

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.result)
        assertEquals(MimeType.PDF, uiState.result?.request?.targetFormat)
        assertEquals(artifactRequest.title, uiState.result?.request?.title)
        assertEquals(artifactRequest.content, uiState.result?.request?.content)
    }

    @Test
    fun `exportToPdf calls renderer and share port`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(
            "title" to artifactRequest.title,
            "content" to URLEncoder.encode(artifactRequest.content, "UTF-8")
        ))
        val mockFile = mockk<File>()
        every { mockFile.absolutePath } returns "/path/to/artifact.pdf"
        coEvery { pdfRenderer.renderToPdf(any(), any()) } returns mockFile

        val viewModel = ArtifactViewModel(pdfRenderer, shareMediaPort, savedStateHandle)
        advanceUntilIdle()
        
        viewModel.exportToPdf()
        advanceUntilIdle()

        verify { shareMediaPort.shareMedia(listOf("/path/to/artifact.pdf"), "application/pdf") }
    }
}
