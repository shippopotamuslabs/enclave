package com.browntowndev.enclave.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.BulkAssignApiProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.DiscoverApiModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.SaveTtsProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val bulkAssignApiProviderUseCase: BulkAssignApiProviderUseCase,
        private val discoverApiModelsUseCase: DiscoverApiModelsUseCase,
        private val saveTtsProviderUseCase: SaveTtsProviderUseCase,
        private val saveMediaProviderUseCase: SaveMediaProviderUseCase,
        private val loggingPort: LoggingPort,
    ) : ViewModel() {
        companion object {
            const val AGE_GATE_CONTENT_DESCRIPTION = "I confirm I am 18 or older"
            const val AUTOMATIC_ERROR_TRACKING_CONTENT_DESCRIPTION = "Allow automatic tracking of errors"
            private const val TAG = "OnboardingViewModel"
        }

        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        private val _events = Channel<OnboardingEvent>(Channel.UNLIMITED)
        val events = _events.receiveAsFlow()

        fun onAgeConfirmedChange(confirmed: Boolean) {
            _uiState.update { it.copy(ageConfirmed = confirmed) }
        }

        fun onAutomaticErrorTrackingChange(enabled: Boolean) {
            _uiState.update { it.copy(automaticErrorTrackingEnabled = enabled) }
        }

        fun onNextStep() {
            val state = _uiState.value
            when (state.currentStep) {
                0 -> {
                    if (!state.ageConfirmed) return
                    _uiState.update { it.copy(currentStep = 1) }
                }
                1 -> {
                    when (state.selectedModelChoice) {
                        ModelChoice.LOCAL -> _uiState.update { it.copy(currentStep = 2) }
                        ModelChoice.API -> {
                            if (state.selectedProvider == null || state.apiKey.isBlank() || state.selectedModelId.isBlank()) return
                            _uiState.update { it.copy(currentStep = 2) }
                        }
                        ModelChoice.UNDECIDED -> return
                    }
                }
                2 -> _uiState.update { it.copy(currentStep = 3) }
                3 -> onCompleteOnboarding()
                else -> Unit
            }
        }

        fun onPreviousStep() {
            val step = _uiState.value.currentStep
            if (step > 0) {
                _uiState.update { it.copy(currentStep = step - 1) }
            }
        }

        fun onModelChoiceSelected(choice: ModelChoice) {
            _uiState.update { it.copy(selectedModelChoice = choice) }
        }

        fun onProviderSelected(provider: ApiProvider) {
            _uiState.update { it.copy(selectedProvider = provider) }
        }

        fun onApiKeyChanged(key: String) {
            _uiState.update { it.copy(apiKey = key) }
        }

        fun onDiscoverModels() {
            val state = _uiState.value
            val provider = state.selectedProvider ?: return
            _uiState.update { it.copy(isDiscoveringModels = true, discoveryError = null) }
            viewModelScope.launch {
                try {
                    val request =
                        ApiModelDiscoveryRequest(
                            provider = provider,
                            currentApiKey = state.apiKey,
                            credentialAlias = null,
                            baseUrl = null,
                            selectedModelId = null,
                        )
                    val result = discoverApiModelsUseCase(request)
                    _uiState.update {
                        it.copy(
                            isDiscoveringModels = false,
                            discoveredModels = result.models,
                            discoveryError = null,
                        )
                    }
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Model discovery failed", e)
                    _uiState.update {
                        it.copy(
                            isDiscoveringModels = false,
                            discoveryError = e.message,
                        )
                    }
                }
            }
        }

        fun onModelSelected(modelId: String) {
            _uiState.update { it.copy(selectedModelId = modelId) }
        }

        fun onSkipMedia() {
            _uiState.update { it.copy(currentStep = 3) }
        }

        fun onSaveTtsProvider(draft: TtsProviderDraft) {
            _uiState.update { it.copy(ttsProviderDraft = draft, isTtsConfigured = true) }
        }

        fun onSaveMediaProvider(draft: MediaProviderDraft) {
            _uiState.update { it.copy(mediaProviderDraft = draft, isMediaConfigured = true) }
        }

        fun onCompleteOnboarding() {
            val state = _uiState.value
            if (!state.ageConfirmed) {
                _uiState.update { it.copy(saveError = "Age confirmation is required") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, saveError = null) }

                settingsRepository.updateAgeConfirmed(true)
                settingsRepository.updateAutomaticErrorTrackingEnabled(state.automaticErrorTrackingEnabled)

                if (state.selectedModelChoice == ModelChoice.API) {
                    val provider =
                        state.selectedProvider ?: run {
                            _uiState.update { it.copy(isSaving = false, saveError = "No provider selected") }
                            return@launch
                        }
                    val credentials =
                        ApiCredentials(
                            id =
                                com.browntowndev.enclave.domain.model.config
                                    .ApiCredentialsId(""),
                            displayName = provider.name,
                            provider = provider,
                            credentialAlias = provider.name.lowercase(),
                        )
                    val result =
                        bulkAssignApiProviderUseCase(
                            credentials = credentials,
                            apiKey = state.apiKey,
                            modelId = state.selectedModelId,
                            displayName = state.selectedModelId,
                        )
                    if (result.isFailure) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveError = result.exceptionOrNull()?.message,
                            )
                        }
                        return@launch
                    }
                }

                // Save deferred TTS provider if configured
                state.ttsProviderDraft?.let { ttsDraft ->
                    val result = saveTtsProviderUseCase(ttsDraft)
                    if (result.isFailure) {
                        loggingPort.error(TAG, "Failed to save TTS provider during completion", result.exceptionOrNull())
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveError = result.exceptionOrNull()?.message ?: "Failed to save TTS provider",
                            )
                        }
                        return@launch
                    }
                }

                // Save deferred Media provider if configured
                state.mediaProviderDraft?.let { mediaDraft ->
                    try {
                        val asset =
                            com.browntowndev.enclave.domain.model.config.MediaProviderAsset(
                                id = mediaDraft.id,
                                displayName = mediaDraft.displayName,
                                provider = mediaDraft.provider,
                                modelName = mediaDraft.modelId,
                                capability = mediaDraft.capability,
                                baseUrl = mediaDraft.baseUrl,
                                credentialAlias = mediaDraft.credentialAlias,
                            )
                        saveMediaProviderUseCase(asset, mediaDraft.apiKey.takeIf { it.isNotBlank() })
                    } catch (e: Exception) {
                        loggingPort.error(TAG, "Failed to save media provider during completion", e)
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveError = e.message ?: "Failed to save media provider",
                            )
                        }
                        return@launch
                    }
                }

                settingsRepository.updateOnboardingCompleted(true)
                _uiState.update { it.copy(isSaving = false, saveError = null) }
                _events.send(OnboardingEvent.NavigateToChat(state.selectedModelChoice))
            }
        }
    }
