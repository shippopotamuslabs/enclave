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
                var decodedTitle = rawTitle

                // Try a few decoding strategies for robustness
                val possibleContents = listOf(
                    rawContent,
                    try { URLDecoder.decode(rawContent, "UTF-8") } catch (e: Exception) { null }
                ).filterNotNull().distinct()

                try {
                    decodedTitle = URLDecoder.decode(rawTitle, "UTF-8")
                } catch (e: Exception) {
                    // Fallback to raw title
                }

                for (jsonContent in possibleContents) {
                    try {
                        // Strategy 1: Full Request Object
                        request = jsonParser.decodeFromString<ArtifactGenerationRequest>(jsonContent)
                        break
                    } catch (e: Exception) {
                        try {
                            // Strategy 2: List of Sections
                            val sections = jsonParser.decodeFromString<List<com.browntowndev.pocketcrew.domain.model.artifact.ArtifactSection>>(jsonContent)
                            request = ArtifactGenerationRequest(title = decodedTitle, sections = sections)
                            break
                        } catch (e2: Exception) {
                            // Continue to next
                        }
                    }
                }

                if (request != null) {
                    _uiState.value = ArtifactUiState(result = ArtifactGenerationResult(request))
                } else {
                    _uiState.value = ArtifactUiState(error = "Failed to parse artifact data. Format may be invalid.")
                }
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
