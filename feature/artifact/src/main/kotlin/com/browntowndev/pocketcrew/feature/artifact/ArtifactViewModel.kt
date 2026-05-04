package com.browntowndev.pocketcrew.feature.artifact

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.data.artifact.PdfArtifactRenderer
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationResult
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class ArtifactViewModel @Inject constructor(
    private val pdfRenderer: PdfArtifactRenderer,
    private val shareMediaPort: ShareMediaPort,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val rawContent: String = savedStateHandle["content"] ?: ""
    private val rawTitle: String = savedStateHandle["title"] ?: "Untitled Artifact"

    private val _uiState = MutableStateFlow(ArtifactUiState())
    val uiState: StateFlow<ArtifactUiState> = _uiState.asStateFlow()

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }
    }

    init {
        parseArtifact()
    }

    private fun parseArtifact() {
        if (rawContent.isBlank()) {
            _uiState.value = ArtifactUiState(error = "No artifact content provided")
            return
        }

        viewModelScope.launch {
            _uiState.value = ArtifactUiState(isLoading = true)
            try {
                var request: ArtifactGenerationRequest? = null
                val decodedTitle = try { URLDecoder.decode(rawTitle, "UTF-8") } catch (e: Exception) { rawTitle }
                val decodedContent = try { URLDecoder.decode(rawContent, "UTF-8") } catch (e: Exception) { rawContent }

                try {
                    // Primary Strategy: Parse as a full JSON request
                    request = jsonParser.decodeFromString<ArtifactGenerationRequest>(decodedContent)
                } catch (e: Exception) {
                    // Fallback: Treat decoded content as raw markdown
                    request = ArtifactGenerationRequest(title = decodedTitle, content = decodedContent)
                }

                _uiState.value = ArtifactUiState(result = ArtifactGenerationResult(request))
            } catch (e: Exception) {
                _uiState.value = ArtifactUiState(error = "Error loading artifact: ${e.message}")
            }
        }
    }

    fun exportToPdf() {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            try {
                // Sanitize filename for export
                val safeFileName = result.request.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_") + ".pdf"
                val file = pdfRenderer.renderToPdf(result, safeFileName)
                // Use absolute path as a URI for the share port
                shareMediaPort.shareMedia(listOf(file.absolutePath), "application/pdf")
                _uiState.value = _uiState.value.copy(isExporting = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false, error = "Failed to export PDF: ${e.message}")
            }
        }
    }
}
