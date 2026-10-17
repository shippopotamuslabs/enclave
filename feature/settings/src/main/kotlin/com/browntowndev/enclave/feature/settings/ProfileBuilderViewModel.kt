package com.browntowndev.enclave.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.usecase.profile.ClearProfileCorpusUseCase
import com.browntowndev.enclave.domain.usecase.profile.GetLatestProfileSynthesisUseCase
import com.browntowndev.enclave.domain.usecase.profile.ObserveApiModelConfigurationsUseCase
import com.browntowndev.enclave.domain.usecase.profile.ObserveProfileAnalysisProgressUseCase
import com.browntowndev.enclave.domain.usecase.profile.PrepareProfileCorpusUseCase
import com.browntowndev.enclave.domain.usecase.profile.SavePsychProfileUseCase
import com.browntowndev.enclave.domain.usecase.profile.StartProfileAnalysisUseCase
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
        private val observeApiModelConfigurations: ObserveApiModelConfigurationsUseCase,
        private val clearProfileCorpus: ClearProfileCorpusUseCase,
        private val prepareProfileCorpus: PrepareProfileCorpusUseCase,
        private val startProfileAnalysis: StartProfileAnalysisUseCase,
        private val observeProfileAnalysisProgress: ObserveProfileAnalysisProgressUseCase,
        private val getLatestProfileSynthesis: GetLatestProfileSynthesisUseCase,
        private val savePsychProfile: SavePsychProfileUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProfileBuilderUiState())
        val uiState = _uiState.asStateFlow()

        private var progressJob: Job? = null

        init {
            loadModels()
        }

        private fun loadModels() {
            viewModelScope.launch {
                observeApiModelConfigurations().collect { configs ->
                    _uiState.update { it.copy(configuredModels = configs.map { config -> config.toUi() }) }
                }
            }
        }

        fun onModelSelected(configId: String) {
            _uiState.update { it.copy(selectedConfigId = configId) }
        }

        fun onFilesSelected(
            uris: List<String>,
            names: List<String>,
        ) {
            val nonZip =
                names.filterNot { name ->
                    name
                        .substringAfterLast('/')
                        .substringAfterLast(':')
                        .lowercase()
                        .endsWith(".zip")
                }
            if (nonZip.isNotEmpty()) {
                val names =
                    nonZip.joinToString { n ->
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
                    val estimate =
                        prepareProfileCorpus(
                            corpusRunId = corpusRunId,
                            fileUris = state.selectedFileUris,
                            configId = state.selectedConfigId?.let { ApiModelConfigurationId(it) },
                        )
                    _uiState.update { it.copy(isPreparingCorpus = false, costEstimate = estimate.toUi()) }
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
                    startProfileAnalysis(state.corpusRunId, ApiModelConfigurationId(configId))
                    observeWorkProgress()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isAnalysisRunning = false,
                            analysisError = "Failed to start analysis: ${e.message}",
                        )
                    }
                }
            }
        }

        private fun observeWorkProgress() {
            // Cancel prior job to prevent stacking collectors
            progressJob?.cancel()
            progressJob =
                viewModelScope.launch {
                    observeProfileAnalysisProgress(_uiState.value.corpusRunId).collect { progress ->
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
                val profileText = getLatestProfileSynthesis(_uiState.value.corpusRunId)
                if (profileText != null) {
                    _uiState.update {
                        it.copy(
                            profileContent = profileText,
                            currentStep = PROFILE_BUILDER_STEP_REVIEW,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAnalysisComplete = false,
                            analysisError = "Analysis completed but no profile was generated. Please try again.",
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
                    savePsychProfile(content)
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                    // Purge raw corpus data in the background — does not block navigation
                    launch { runCatching { clearProfileCorpus(state.corpusRunId) } }
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
                    runCatching { clearProfileCorpus(state.corpusRunId) }
                }
            }
        }
    }
