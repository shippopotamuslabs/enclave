package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository] fake with failure toggle (ENC-17). */
class FakeSettingsRepository(
    initial: SettingsData = SettingsData(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    var shouldFail: Boolean = false

    /** Recorded [updateAllowMemories] values, in call order (ENC-28). */
    val allowMemoriesUpdates = mutableListOf<Boolean>()

    /** Recorded [updateCustomizationEnabled] values, in call order (ENC-28). */
    val customizationEnabledUpdates = mutableListOf<Boolean>()

    /** Recorded [updateSelectedPromptOption] values, in call order (ENC-28). */
    val selectedPromptOptionUpdates = mutableListOf<SystemPromptOption>()

    override val settingsFlow: Flow<SettingsData> = state.asStateFlow()

    fun seed(data: SettingsData) {
        state.value = data
    }

    private fun mutate(transform: (SettingsData) -> SettingsData) {
        check(!shouldFail) { "FakeSettingsRepository: shouldFail" }
        state.update(transform)
    }

    override suspend fun updateTheme(theme: AppTheme) = mutate { it.copy(theme = theme) }

    override suspend fun updateHapticResponse(value: Boolean) = mutate { it.copy(hapticResponse = value) }

    override suspend fun updateCustomizationEnabled(enabled: Boolean) {
        customizationEnabledUpdates += enabled
        mutate { it.copy(customizationEnabled = enabled) }
    }

    override suspend fun updateSelectedPromptOption(option: SystemPromptOption) {
        selectedPromptOptionUpdates += option
        mutate { it.copy(selectedPromptOption = option) }
    }

    override suspend fun updateCustomPromptText(text: String) = mutate { it.copy(customPromptText = text) }

    override suspend fun updateAllowMemories(allowed: Boolean) {
        allowMemoriesUpdates += allowed
        mutate { it.copy(allowMemories = allowed) }
    }

    override suspend fun updateSearchEnabled(enabled: Boolean) = mutate { it.copy(searchEnabled = enabled) }

    override suspend fun updateSearchProvider(provider: SearchProvider) = mutate { it.copy(searchProvider = provider) }

    override suspend fun saveTavilyApiKey(apiKey: String) = mutate { it.copy(tavilyKeyPresent = apiKey.isNotBlank()) }

    override suspend fun clearTavilyApiKey() = mutate { it.copy(tavilyKeyPresent = false) }

    override suspend fun saveExaApiKey(apiKey: String) = mutate { it.copy(exaKeyPresent = apiKey.isNotBlank()) }

    override suspend fun clearExaApiKey() = mutate { it.copy(exaKeyPresent = false) }

    override suspend fun updateXSearchEnabled(enabled: Boolean) = mutate { it.copy(xSearchEnabled = enabled) }

    override suspend fun saveXApiKey(apiKey: String) = mutate { it.copy(xApiKeyPresent = apiKey.isNotBlank()) }

    override suspend fun clearXApiKey() = mutate { it.copy(xApiKeyPresent = false) }

    override suspend fun updateShowSearchProviderSwitchAlert(show: Boolean) = mutate { it.copy(showSearchProviderSwitchAlert = show) }

    override suspend fun updateBackgroundInferenceEnabled(enabled: Boolean) = mutate { it.copy(backgroundInferenceEnabled = enabled) }

    override suspend fun updateOnboardingCompleted(completed: Boolean) = mutate { it.copy(onboardingCompleted = completed) }

    override suspend fun updateAgeConfirmed(confirmed: Boolean) = mutate { it.copy(ageConfirmed = confirmed) }

    override suspend fun updateAutomaticErrorTrackingEnabled(enabled: Boolean) = mutate { it.copy(automaticErrorTrackingEnabled = enabled) }
}
