package com.browntowndev.enclave.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisPort
import com.browntowndev.enclave.domain.port.profile.ProfileExportIngestionPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileBuilderViewModel
    @Inject
    constructor(
        private val apiModelRepository: ApiModelRepositoryPort,
        private val ingestionPort: ProfileExportIngestionPort,
        private val analysisPort: ProfileAnalysisPort,
        private val memoriesRepository: MemoriesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProfileBuilderUiState())
        val uiState = _uiState.asStateFlow()

        private var progressJob: Job? = null

        init {
            loadModels()
        }

        private fun loadModels() {
            viewModelScope.launch {
                apiModelRepository.observeAllConfigurations().collect { configs ->
                    _uiState.update { it.copy(configuredModels = configs) }
                }
            }
        }

        fun onModelSelected(configId: ApiModelConfigurationId) {
            _uiState.update { it.copy(selectedConfigId = configId) }
        }

        fun onFilesSelected(uris: List<String>, names: List<String>) {
            val nonZip = names.filterNot { name ->
                name.substringAfterLast('/').substringAfterLast(':').lowercase().endsWith(".zip")
            }
            if (nonZip.isNotEmpty()) {
                val names = nonZip.joinToString { n ->
                    n.substringAfterLast('/').substringAfterLast(':')
                }
                _uiState.update {
                    it.copy(errorMessage = "Only .zip export files are supported. Unsupported: $names")
                }
                return
            }
            _uiState.update {
                it.copy(selectedFileUris = uris, selectedFileNames = names, errorMessage = null)
            }
        }

        fun goToStep(step: Int) {
            _uiState.update { it.copy(currentStep = step, errorMessage = null) }
        }

        fun onProceedFromFiles() {
            val state = _uiState.value
            if (state.selectedFileUris.isEmpty()) return
            _uiState.update { it.copy(isPreparingCorpus = true, currentStep = PROFILE_BUILDER_STEP_COST) }
            viewModelScope.launch {
                try {
                    val corpusRunId = state.corpusRunId
                    val estimate = analysisPort.prepareCorpus(
                        corpusRunId = corpusRunId,
                        fileUris = state.selectedFileUris,
                        configId = state.selectedConfigId
                    )
                    _uiState.update { it.copy(isPreparingCorpus = false, costEstimate = estimate) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isPreparingCorpus = false,
                            errorMessage = "Failed to prepare corpus: ${e.message}",
                            currentStep = PROFILE_BUILDER_STEP_FILES,
                        )
                    }
                }
            }
        }

        fun startAnalysis() {
            val state = _uiState.value
            val configId = state.selectedConfigId ?: return
            
            // Double-start protection guard
            if (state.isAnalysisRunning) return

            _uiState.update {
                it.copy(
                    isAnalysisRunning = true,
                    currentStep = PROFILE_BUILDER_STEP_PROGRESS,
                )
            }

            viewModelScope.launch {
                try {
                    analysisPort.startAnalysis(state.corpusRunId, configId)
                    observeWorkProgress()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isAnalysisRunning = false,
                            analysisError = "Failed to start analysis: ${e.message}"
                        )
                    }
                }
            }
        }

        private fun observeWorkProgress() {
            // Cancel prior job to prevent stacking collectors
            progressJob?.cancel()
            progressJob = viewModelScope.launch {
                analysisPort.observeAnalysisProgress(_uiState.value.corpusRunId).collect { progress ->
                    _uiState.update {
                        it.copy(
                            currentPass = progress.currentPass,
                            totalPasses = progress.totalPasses,
                            isAnalysisRunning = progress.isRunning,
                            isAnalysisComplete = progress.isComplete,
                            analysisError = progress.error,
                        )
                    }

                    if (progress.isComplete) {
                        loadProfileContent()
                    }
                }
            }
        }

        private fun loadProfileContent() {
            viewModelScope.launch {
                val profileText = analysisPort.getLatestProfileSynthesis(_uiState.value.corpusRunId)
                if (profileText != null) {
                    _uiState.update {
                        it.copy(
                            profileContent = profileText,
                            currentStep = PROFILE_BUILDER_STEP_REVIEW,
                        )
                    }
                }
            }
        }

        fun onProfileContentEdited(content: String) {
            _uiState.update { it.copy(profileContent = content) }
        }

        fun saveProfile() {
            val state = _uiState.value
            val content = state.profileContent.trim()
            if (content.isBlank()) return
            _uiState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                try {
                    // Single write policy: upsert memory on save
                    val existing = memoriesRepository.getCoreMemories()
                        .firstOrNull { it.category == com.browntowndev.enclave.domain.model.memory.MemoryCategory.PSYCH_PROFILE }
                    if (existing != null) {
                        memoriesRepository.updateMemory(
                            existing.id,
                            content,
                            com.browntowndev.enclave.domain.model.memory.MemoryCategory.PSYCH_PROFILE,
                        )
                    } else {
                        memoriesRepository.insertMemory(
                            com.browntowndev.enclave.domain.model.memory.MemoryCategory.PSYCH_PROFILE,
                            content,
                        )
                    }
                    // Purge all raw corpus data now that the profile is saved
                    runCatching { ingestionPort.clearAll(state.corpusRunId) }
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "Save failed: ${e.message}") }
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            val state = _uiState.value
            if (!state.saved) {
                // Purge transient data if flow was cancelled or abandoned before saving
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    runCatching { ingestionPort.clearAll(state.corpusRunId) }
                }
            }
        }
    }
