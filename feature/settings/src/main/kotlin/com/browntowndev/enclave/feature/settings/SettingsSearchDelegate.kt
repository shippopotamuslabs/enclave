package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsSearchDelegate
    @Inject
    constructor(
        private val preferencesUseCases: SettingsPreferencesUseCases,
        private val cloudSyncPort: CloudSyncPort,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onSearchEnabledChange(
            searchSkillState: MutableStateFlow<SearchSkillTransientState>,
            enabled: Boolean,
        ) {
            searchSkillState.update { it.copy(enabled = enabled) }
        }

        fun onTavilyApiKeyChange(
            currentTavilyApiKey: MutableStateFlow<String>,
            searchSkillState: MutableStateFlow<SearchSkillTransientState>,
            tavilyKeyPresent: Boolean,
            key: String,
        ) {
            currentTavilyApiKey.value = key
            if (key.isBlank() && !tavilyKeyPresent) {
                searchSkillState.update { it.copy(enabled = false) }
            }
        }

        fun onClearTavilyApiKey(
            scope: CoroutineScope,
            tag: String,
            currentTavilyApiKey: MutableStateFlow<String>,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to clear Tavily API key", "Failed to clear search key")) {
                preferencesUseCases.clearTavilyApiKey()
                currentTavilyApiKey.value = ""
            }
        }

        fun onExaApiKeyChange(
            currentExaApiKey: MutableStateFlow<String>,
            searchSkillState: MutableStateFlow<SearchSkillTransientState>,
            exaKeyPresent: Boolean,
            key: String,
        ) {
            currentExaApiKey.value = key
            if (key.isBlank() && !exaKeyPresent) {
                searchSkillState.update { it.copy(enabled = false) }
            }
        }

        fun onClearExaApiKey(
            scope: CoroutineScope,
            tag: String,
            currentExaApiKey: MutableStateFlow<String>,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to clear Exa API key", "Failed to clear search key")) {
                preferencesUseCases.clearExaApiKey()
                currentExaApiKey.value = ""
            }
        }

        fun onSearchProviderChange(
            searchSkillState: MutableStateFlow<SearchSkillTransientState>,
            provider: SearchProvider,
        ) {
            searchSkillState.update { it.copy(selectedProvider = provider) }
        }

        fun onUpdateShowSearchProviderSwitchAlert(
            scope: CoroutineScope,
            tag: String,
            show: Boolean,
        ) {
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to update alert preference",
                    "Failed to update preference",
                ),
            ) {
                preferencesUseCases.updateShowSearchProviderSwitchAlert(show)
            }
        }

        fun onSaveSearchSkillSettings(
            scope: CoroutineScope,
            tag: String,
            searchSkillEditorState: SearchSkillEditorUiState,
            transientSearchSkillState: MutableStateFlow<SearchSkillTransientState>,
            currentTavilyApiKey: MutableStateFlow<String>,
            currentExaApiKey: MutableStateFlow<String>,
            onSuccess: () -> Unit,
        ) {
            val enabled = transientSearchSkillState.value.enabled ?: searchSkillEditorState.enabled
            val selectedProvider = transientSearchSkillState.value.selectedProvider ?: searchSkillEditorState.selectedProvider

            val pendingTavilyKey = currentTavilyApiKey.value.trim()
            val pendingExaKey = currentExaApiKey.value.trim()

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to save search skill settings",
                    "Failed to save search settings",
                ),
            ) {
                if (enabled) {
                    when (selectedProvider) {
                        SearchProvider.TAVILY -> {
                            if (pendingTavilyKey.isBlank() && !searchSkillEditorState.tavilyKeyPresent) {
                                throw IllegalStateException("A Tavily API key is required to enable web search with Tavily")
                            }
                        }
                        SearchProvider.EXA -> {
                            if (pendingExaKey.isBlank() && !searchSkillEditorState.exaKeyPresent) {
                                throw IllegalStateException("An Exa API key is required to enable web search with Exa")
                            }
                        }
                    }
                }

                preferencesUseCases.updateSearchEnabled(enabled)
                preferencesUseCases.updateSearchProvider(selectedProvider)

                if (pendingTavilyKey.isNotBlank()) {
                    preferencesUseCases.saveTavilyApiKey(pendingTavilyKey)
                }
                if (pendingExaKey.isNotBlank()) {
                    preferencesUseCases.saveExaApiKey(pendingExaKey)
                }

                cloudSyncPort.triggerSync()

                currentTavilyApiKey.value = ""
                currentExaApiKey.value = ""
                transientSearchSkillState.value = SearchSkillTransientState()
                onSuccess()
            }
        }

        fun onXSearchEnabledChange(
            xSearchState: MutableStateFlow<XSearchTransientState>,
            enabled: Boolean,
        ) {
            xSearchState.update { it.copy(enabled = enabled) }
        }

        fun onXApiKeyChange(
            currentXApiKey: MutableStateFlow<String>,
            key: String,
        ) {
            currentXApiKey.value = key
        }

        fun onClearXApiKey(
            scope: CoroutineScope,
            tag: String,
            currentXApiKey: MutableStateFlow<String>,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to clear X API key", "Failed to clear X key")) {
                preferencesUseCases.clearXApiKey()
                currentXApiKey.value = ""
            }
        }

        fun onSaveXSearchSettings(
            scope: CoroutineScope,
            tag: String,
            xSearchEditorState: XSearchEditorUiState,
            transientXSearchState: MutableStateFlow<XSearchTransientState>,
            currentXApiKey: MutableStateFlow<String>,
            onSuccess: () -> Unit,
        ) {
            val enabled = transientXSearchState.value.enabled ?: xSearchEditorState.enabled
            val pendingKey = currentXApiKey.value.trim()

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to save X search settings",
                    "Failed to save X settings",
                ),
            ) {
                if (enabled && pendingKey.isBlank() && !xSearchEditorState.apiKeyPresent) {
                    throw IllegalStateException("An X API key is required to enable X search")
                }

                preferencesUseCases.updateXSearchEnabled(enabled)
                if (pendingKey.isNotBlank()) {
                    preferencesUseCases.saveXApiKey(pendingKey)
                }

                cloudSyncPort.triggerSync()

                currentXApiKey.value = ""
                transientXSearchState.value = XSearchTransientState()
                onSuccess()
            }
        }
    }
